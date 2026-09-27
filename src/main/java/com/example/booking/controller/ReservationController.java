package com.example.booking.controller;

import com.example.booking.dto.PageResponse;
import com.example.booking.dto.ReservationCreateRequest;
import com.example.booking.dto.ReservationResponse;
import com.example.booking.dto.ReservationUpdateRequest;
import com.example.booking.entity.ReservationStatus;
import com.example.booking.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;

@Tag(name = "Reservations", description = "USER sees and creates own reservations; ADMIN has full access")
@RestController
@RequestMapping("/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','USER')")
    public ResponseEntity<ReservationResponse> create(@Valid @RequestBody ReservationCreateRequest request,
                                                      Authentication authentication) {
        ReservationResponse created = reservationService.create(request, authentication);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    // Any authenticated user. The service scopes the results: ADMIN -> all, USER -> own only.
    @Operation(summary = "List reservations (filter, paginate, sort)",
            description = "ADMIN sees all reservations, USER sees only their own. "
                    + "sortBy: id, price, startTime, endTime, status. direction: asc or desc.")
    @GetMapping
    public PageResponse<ReservationResponse> list(
            @RequestParam(required = false) ReservationStatus status,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String direction,
            Authentication authentication) {
        return reservationService.search(status, minPrice, maxPrice, page, size, sortBy, direction, authentication);
    }

    // Ownership is checked in the service: USER -> own only, ADMIN -> any
    @GetMapping("/{id}")
    public ReservationResponse getById(@PathVariable Long id, Authentication authentication) {
        return reservationService.findById(id, authentication);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ReservationResponse update(@PathVariable Long id,
                                      @Valid @RequestBody ReservationUpdateRequest request) {
        return reservationService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        reservationService.delete(id);
        return ResponseEntity.noContent().build();
    }
}