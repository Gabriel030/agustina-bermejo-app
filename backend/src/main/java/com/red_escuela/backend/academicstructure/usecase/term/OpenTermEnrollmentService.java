package com.red_escuela.backend.academicstructure.usecase.term;

import com.red_escuela.backend.academicstructure.service.TermEnrollmentOpening;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class OpenTermEnrollmentService implements OpenTermEnrollmentUseCase {

    private final TermEnrollmentOpening termEnrollmentOpening;

    public OpenTermEnrollmentService(TermEnrollmentOpening termEnrollmentOpening) {
        this.termEnrollmentOpening = termEnrollmentOpening;
    }

    @Override
    public void open(Long id) {
        termEnrollmentOpening.open(id);
    }
}
