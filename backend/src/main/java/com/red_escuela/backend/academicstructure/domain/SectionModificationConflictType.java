package com.red_escuela.backend.academicstructure.domain;

public enum SectionModificationConflictType {
    SLOT_OUTSIDE_SHIFT,
    CLASSROOM_CAPACITY_EXCEEDED,
    CLASSROOM_OCCUPIED,
    INSTRUCTOR_BUSY,
    SLOTS_OVERLAP
}
