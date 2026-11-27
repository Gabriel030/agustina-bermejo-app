package com.red_escuela.backend.academicstructure.domain;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class CurriculumPlanNotFoundException extends NotFoundException {
    public CurriculumPlanNotFoundException(Long id) {
        super("Plan de estudios no encontrado: " + id);
    }
}
