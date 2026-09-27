package com.example.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResourceRequest(
        @NotBlank(message = "name is required")
        @Size(max = 100, message = "name must be at most 100 characters")
        String name,

        @Size(max = 500, message = "description must be at most 500 characters")
        String description,

        @NotBlank(message = "type is required (e.g. ROOM, VEHICLE, EQUIPMENT)")
        @Size(max = 50, message = "type must be at most 50 characters")
        String type
) {}