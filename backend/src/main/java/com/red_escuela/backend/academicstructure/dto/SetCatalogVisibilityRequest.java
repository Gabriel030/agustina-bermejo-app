package com.red_escuela.backend.academicstructure.dto;

import jakarta.validation.constraints.NotNull;

public record SetCatalogVisibilityRequest(@NotNull Boolean visible) {
}
