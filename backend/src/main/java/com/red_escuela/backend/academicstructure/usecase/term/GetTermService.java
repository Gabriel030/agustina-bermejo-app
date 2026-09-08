package com.red_escuela.backend.academicstructure.usecase.term;

import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.service.TermFinder;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetTermService implements GetTermUseCase {

    private final TermFinder termFinder;

    public GetTermService(TermFinder termFinder) {
        this.termFinder = termFinder;
    }

    @Override
    public Term get(Long id) {
        return termFinder.findOrThrow(id);
    }
}
