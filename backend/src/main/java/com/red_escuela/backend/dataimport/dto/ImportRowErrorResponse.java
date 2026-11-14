package com.red_escuela.backend.dataimport.dto;

import com.red_escuela.backend.dataimport.domain.ImportEntityType;
import com.red_escuela.backend.dataimport.entity.ImportRowStatus;

public record ImportRowErrorResponse(ImportEntityType entityType, int rowNumber, ImportRowStatus rowStatus,
        String column, String code, String message) {
}
