package com.red_escuela.backend.academicstructure.dto;


import java.time.LocalDateTime;

public record SectionInstructorResponse(
        Long id, Long instructorId, String instructorNombre, InstructorRole role,
        LocalDateTime startAt, LocalDateTime endAt, Integer hoursPerWeek) {
}
