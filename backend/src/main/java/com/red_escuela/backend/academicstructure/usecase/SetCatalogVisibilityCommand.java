package com.red_escuela.backend.academicstructure.usecase;

import com.red_escuela.backend.academicstructure.entity.CatalogArea;

public record SetCatalogVisibilityCommand(Long offeringId, boolean visible, CatalogArea area) {
}
