package com.red_escuela.backend.academicstructure.usecase.term;

import com.red_escuela.backend.academicstructure.service.TermEnrollmentOverrideClearing;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ClearTermEnrollmentOverrideService implements ClearTermEnrollmentOverrideUseCase {

    private final TermEnrollmentOverrideClearing termEnrollmentOverrideClearing;

    public ClearTermEnrollmentOverrideService(TermEnrollmentOverrideClearing termEnrollmentOverrideClearing) {
        this.termEnrollmentOverrideClearing = termEnrollmentOverrideClearing;
    }

    @Override
    public void clear(Long id) {
        termEnrollmentOverrideClearing.clear(id);
    }
}
