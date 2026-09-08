package com.red_escuela.backend.academicstructure.usecase.term;

import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.entity.TermEnrollmentOverride;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import com.red_escuela.backend.academicstructure.service.TermFinder;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class OpenTermEnrollmentService implements OpenTermEnrollmentUseCase {

    private final TermRepository termRepository;
    private final TermFinder termFinder;

    public OpenTermEnrollmentService(TermRepository termRepository, TermFinder termFinder) {
        this.termRepository = termRepository;
        this.termFinder = termFinder;
    }

    @Override
    @Transactional
    public void open(Long id) {
        Term term = termFinder.findOrThrow(id);
        term.setEnrollmentOverride(TermEnrollmentOverride.OPEN);
        termRepository.save(term);
    }
}
