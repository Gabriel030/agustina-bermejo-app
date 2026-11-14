package com.red_escuela.backend.dataimport.repository;

import com.red_escuela.backend.dataimport.domain.ImportEntityType;

public record ImportCreatedCount(ImportEntityType entityType, long total) {
}
