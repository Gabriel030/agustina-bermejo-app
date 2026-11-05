package com.red_escuela.backend.academicstructure.usecase.term;

import com.red_escuela.backend.academicstructure.service.TermEnrollmentClosing;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class CloseTermEnrollmentService implements CloseTermEnrollmentUseCase {

    private final TermEnrollmentClosing termEnrollmentClosing;

    public CloseTermEnrollmentService(TermEnrollmentClosing termEnrollmentClosing) {
        this.termEnrollmentClosing = termEnrollmentClosing;
    }

    @Override
    public void close(Long id) {
        termEnrollmentClosing.close(id);
    }
}
