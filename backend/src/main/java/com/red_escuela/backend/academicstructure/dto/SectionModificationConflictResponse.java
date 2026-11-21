package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.domain.SectionModificationConflictType;

public record SectionModificationConflictResponse(
        int slotIndex,
        SectionModificationConflictType type,
        String conflictingSectionLabel) {
}
