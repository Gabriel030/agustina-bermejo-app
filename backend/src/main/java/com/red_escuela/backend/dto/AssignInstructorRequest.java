package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.InstructorRole;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record AssignInstructorRequest(
        @NotNull Long instructorId, @NotNull InstructorRole role,
        LocalDateTime startAt, LocalDateTime endAt, Integer hoursPerWeek, String notes) {
}
