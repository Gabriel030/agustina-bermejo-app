package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.domain.SectionModificationConflictType;

import java.util.function.Supplier;

public record SectionModificationConflict(
        int slotIndex,
        SectionModificationConflictType type,
        String conflictingSectionLabel,
        Supplier<? extends RuntimeException> exception) {
}
