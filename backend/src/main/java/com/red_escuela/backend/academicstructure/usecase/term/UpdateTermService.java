package com.red_escuela.backend.academicstructure.usecase.term;

import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.service.TermUpdate;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class UpdateTermService implements UpdateTermUseCase {

    private final TermUpdate termUpdate;

    public UpdateTermService(TermUpdate termUpdate) {
        this.termUpdate = termUpdate;
    }

    @Override
    public Term update(UpdateTermCommand command) {
        return termUpdate.update(command.id(), command.request());
    }
}
