package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.entity.Classroom;

public record ClassroomResponse(
        Long id,
        String name,
        Integer capacity,
        String status
) {
    public static ClassroomResponse from(Classroom c) {
        return new ClassroomResponse(c.getId(), c.getName(), c.getCapacity(), c.getStatus().name());
    }
}
