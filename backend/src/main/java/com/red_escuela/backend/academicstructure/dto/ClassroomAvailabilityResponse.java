package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.entity.Classroom;

public record ClassroomAvailabilityResponse(
        Long id,
        String name,
        Integer capacity,
        boolean available
) {
    public static ClassroomAvailabilityResponse of(Classroom c, boolean available) {
        return new ClassroomAvailabilityResponse(c.getId(), c.getName(), c.getCapacity(), available);
    }
}
