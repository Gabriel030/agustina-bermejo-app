package com.red_escuela.backend.academicstructure.usecase.term;

import com.red_escuela.backend.academicstructure.service.TermActivation;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ActivateTermService implements ActivateTermUseCase {

    private final TermActivation termActivation;

    public ActivateTermService(TermActivation termActivation) {
        this.termActivation = termActivation;
    }

    @Override
    public void activate(Long id) {
        termActivation.activate(id);
    }
}
