package com.example.booking.repository;

import com.example.booking.entity.Reservation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface ReservationRepository
        extends JpaRepository<Reservation, Long>, JpaSpecificationExecutor<Reservation> {

    /** Load resource + user together with the page of reservations (avoids N+1). */
    @Override
    @EntityGraph(attributePaths = {"resource", "user"})
    Page<Reservation> findAll(Specification<Reservation> spec, Pageable pageable);

    /**
     * True if another non-cancelled reservation on the same resource overlaps [start, end).
     * Pass excludeId = -1 when creating (nothing to exclude).
     */
    @Query("""
            select count(r) > 0 from Reservation r
            where r.resource.id = :resourceId
              and r.status <> com.example.booking.entity.ReservationStatus.CANCELLED
              and r.id <> :excludeId
              and r.startTime < :end
              and r.endTime > :start
            """)
    boolean existsOverlap(@Param("resourceId") Long resourceId,
                          @Param("start") LocalDateTime start,
                          @Param("end") LocalDateTime end,
                          @Param("excludeId") Long excludeId);
}