package com.red_escuela.backend.academicstructure.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record UpdateCohortResponsiblesRequest(@NotEmpty List<Long> responsiblePreceptorIds) {
}
