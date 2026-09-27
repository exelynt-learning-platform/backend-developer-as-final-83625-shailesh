package com.example.booking.dto;

public record LoginResponse(
        String token,
        String tokenType,
        long expiresInMs,
        String username,
        String role
) {}