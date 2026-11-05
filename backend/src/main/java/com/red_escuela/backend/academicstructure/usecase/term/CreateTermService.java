package com.red_escuela.backend.academicstructure.usecase.term;

import com.red_escuela.backend.academicstructure.dto.CreateTermRequest;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.service.TermCreation;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class CreateTermService implements CreateTermUseCase {

    private final TermCreation termCreation;

    public CreateTermService(TermCreation termCreation) {
        this.termCreation = termCreation;
    }

    @Override
    public Term create(CreateTermRequest request) {
        return termCreation.create(request);
    }
}
