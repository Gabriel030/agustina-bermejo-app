package com.red_escuela.backend.academicstructure.usecase;

import com.red_escuela.backend.academicstructure.domain.CatalogArea;

public record SetCatalogVisibilityCommand(Long offeringId, boolean visible, CatalogArea area) {
}
