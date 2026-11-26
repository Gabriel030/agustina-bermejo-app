package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.entity.CatalogArea;
import jakarta.validation.constraints.NotNull;
import org.springframework.lang.Nullable;

public record SetCatalogVisibilityRequest(@NotNull Boolean visible, @Nullable CatalogArea area) {
}
