package com.example.booking.service;

import com.example.booking.dto.PageResponse;
import com.example.booking.dto.ReservationCreateRequest;
import com.example.booking.dto.ReservationResponse;
import com.example.booking.dto.ReservationUpdateRequest;
import com.example.booking.entity.Reservation;
import com.example.booking.entity.ReservationStatus;
import com.example.booking.entity.Resource;
import com.example.booking.entity.User;
import com.example.booking.exception.BadRequestException;
import com.example.booking.exception.ConflictException;
import com.example.booking.exception.ResourceNotFoundException;
import com.example.booking.repository.ReservationRepository;
import com.example.booking.repository.ReservationSpecifications;
import com.example.booking.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

@Service
public class ReservationService {

    private static final long NO_EXCLUDE = -1L;

    // Whitelist: stops clients from sorting by arbitrary/sensitive paths like "user.password"
    private static final Set<String> SORTABLE_FIELDS =
            Set.of("id", "price", "startTime", "endTime", "status");
    private static final int MAX_PAGE_SIZE = 100;

    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final ResourceService resourceService;

    public ReservationService(ReservationRepository reservationRepository,
                              UserRepository userRepository,
                              ResourceService resourceService) {
        this.reservationRepository = reservationRepository;
        this.userRepository = userRepository;
        this.resourceService = resourceService;
    }

    // ---------------- CREATE (ADMIN or USER) ----------------

    @Transactional
    public ReservationResponse create(ReservationCreateRequest request, Authentication auth) {
        validateTimes(request.startTime(), request.endTime());

        // Identity comes from the JWT (auth), never from the request body
        User owner = userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + auth.getName()));

        Resource resource = resourceService.getOrThrow(request.resourceId());
        assertNoOverlap(resource.getId(), request.startTime(), request.endTime(), NO_EXCLUDE);

        Reservation reservation = new Reservation();
        reservation.setUser(owner);
        reservation.setResource(resource);
        reservation.setStartTime(request.startTime());
        reservation.setEndTime(request.endTime());
        reservation.setPrice(request.price());
        reservation.setStatus(ReservationStatus.PENDING); // always PENDING on creation

        return ReservationResponse.from(reservationRepository.save(reservation));
    }

    // ---------------- READ ONE (owner or ADMIN) ----------------

    @Transactional(readOnly = true)
    public ReservationResponse findById(Long id, Authentication auth) {
        Reservation reservation = getOrThrow(id);
        assertCanAccess(reservation, auth);
        return ReservationResponse.from(reservation);
    }

    // ---------------- LIST (ADMIN: all, USER: own only) ----------------

    @Transactional(readOnly = true)
    public PageResponse<ReservationResponse> search(ReservationStatus status,
                                                    BigDecimal minPrice,
                                                    BigDecimal maxPrice,
                                                    int page,
                                                    int size,
                                                    String sortBy,
                                                    String direction,
                                                    Authentication auth) {
        validateSearchParams(minPrice, maxPrice, page, size, sortBy);

        Specification<Reservation> spec = Specification
                .<Reservation>where(ReservationSpecifications.hasStatus(status))
                .and(ReservationSpecifications.priceAtLeast(minPrice))
                .and(ReservationSpecifications.priceAtMost(maxPrice));

        // Ownership rule applied INSIDE the query: a USER can never receive others' rows
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin) {
            spec = spec.and(ReservationSpecifications.ownedBy(auth.getName()));
        }

        // Secondary sort on id keeps page boundaries stable when sort values tie
        Sort sort = Sort.by(parseDirection(direction), sortBy);

        if (!sortBy.equals("id")) {
            sort = sort.and(Sort.by(Sort.Direction.ASC, "id"));
        }
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Reservation> result = reservationRepository.findAll(spec, pageable);
        return PageResponse.from(result.map(ReservationResponse::from));
    }

    private void validateSearchParams(BigDecimal minPrice, BigDecimal maxPrice,
                                      int page, int size, String sortBy) {
        if (page < 0) {
            throw new BadRequestException("page must be 0 or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (minPrice != null && minPrice.signum() < 0) {
            throw new BadRequestException("minPrice must not be negative");
        }
        if (maxPrice != null && maxPrice.signum() < 0) {
            throw new BadRequestException("maxPrice must not be negative");
        }
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new BadRequestException("minPrice must not be greater than maxPrice");
        }
        if (!SORTABLE_FIELDS.contains(sortBy)) {
            throw new BadRequestException("sortBy must be one of " + SORTABLE_FIELDS);
        }
    }

    private Sort.Direction parseDirection(String direction) {
        try {
            return Sort.Direction.fromString(direction); // case-insensitive
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("direction must be 'asc' or 'desc'");
        }
    }

    // ---------------- UPDATE (ADMIN only, enforced in controller) ----------------

    @Transactional
    public ReservationResponse update(Long id, ReservationUpdateRequest request) {
        Reservation reservation = getOrThrow(id);
        validateTimes(request.startTime(), request.endTime());

        Resource resource = resourceService.getOrThrow(request.resourceId());

        if (request.status() != ReservationStatus.CANCELLED) {
            assertNoOverlap(resource.getId(), request.startTime(), request.endTime(), id);
        }

        reservation.setResource(resource);
        reservation.setStartTime(request.startTime());
        reservation.setEndTime(request.endTime());
        reservation.setPrice(request.price());
        reservation.setStatus(request.status());

        return ReservationResponse.from(reservationRepository.save(reservation));
    }

    // ---------------- DELETE (ADMIN only, enforced in controller) ----------------

    @Transactional
    public void delete(Long id) {
        reservationRepository.delete(getOrThrow(id));
    }

    // ---------------- helpers ----------------

    private Reservation getOrThrow(Long id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found with id " + id));
    }

    private void assertCanAccess(Reservation reservation, Authentication auth) {
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        boolean isOwner = reservation.getUser().getUsername().equals(auth.getName());

        if (!isAdmin && !isOwner) {
            throw new AccessDeniedException("You can only access your own reservations");
        }
    }

    private void validateTimes(LocalDateTime start, LocalDateTime end) {
        if (!end.isAfter(start)) {
            throw new BadRequestException("endTime must be after startTime");
        }
    }

    private void assertNoOverlap(Long resourceId, LocalDateTime start, LocalDateTime end, Long excludeId) {
        if (reservationRepository.existsOverlap(resourceId, start, end, excludeId)) {
            throw new ConflictException("The resource is already reserved for the requested time range");
        }
    }
}