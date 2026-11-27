package com.red_escuela.backend.academicstructure.entity;

import org.springframework.modulith.NamedInterface;

@NamedInterface("model")
public enum CurriculumPlanStatus {
    DRAFT,
    ACTIVE,
    EXPIRED
}
