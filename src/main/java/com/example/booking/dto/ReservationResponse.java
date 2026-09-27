package com.example.booking.dto;

import com.example.booking.entity.Reservation;
import com.example.booking.entity.ReservationStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ReservationResponse(
        Long id,
        Long resourceId,
        String resourceName,
        String username,
        LocalDateTime startTime,
        LocalDateTime endTime,
        BigDecimal price,
        ReservationStatus status
) {
    // Call inside a @Transactional method: resource and user are LAZY
    public static ReservationResponse from(Reservation r) {
        return new ReservationResponse(
                r.getId(),
                r.getResource().getId(),
                r.getResource().getName(),
                r.getUser().getUsername(),
                r.getStartTime(),
                r.getEndTime(),
                r.getPrice(),
                r.getStatus());
    }
}