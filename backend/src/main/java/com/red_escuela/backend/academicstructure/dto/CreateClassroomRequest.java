package com.red_escuela.backend.academicstructure.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateClassroomRequest(
        @NotBlank String name,
        @NotNull @Min(1) Integer capacity
) {}
