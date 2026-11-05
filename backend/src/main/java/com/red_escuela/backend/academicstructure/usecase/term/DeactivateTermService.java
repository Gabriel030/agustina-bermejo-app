package com.red_escuela.backend.academicstructure.usecase.term;

import com.red_escuela.backend.academicstructure.service.TermDeactivation;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class DeactivateTermService implements DeactivateTermUseCase {

    private final TermDeactivation termDeactivation;

    public DeactivateTermService(TermDeactivation termDeactivation) {
        this.termDeactivation = termDeactivation;
    }

    @Override
    public void deactivate(Long id) {
        termDeactivation.deactivate(id);
    }
}
