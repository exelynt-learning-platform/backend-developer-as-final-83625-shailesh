package com.example.booking.dto;

import com.example.booking.entity.Resource;

public record ResourceResponse(
        Long id,
        String name,
        String description,
        String type
) {
    public static ResourceResponse from(Resource r) {
        return new ResourceResponse(r.getId(), r.getName(), r.getDescription(), r.getType());
    }
}