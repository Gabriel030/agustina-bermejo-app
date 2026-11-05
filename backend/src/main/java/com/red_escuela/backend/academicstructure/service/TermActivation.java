package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import com.red_escuela.backend.shared.domain.StatusType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TermActivation {

    private final TermRepository termRepository;
    private final TermFinder termFinder;
    private final ActiveTermUniquenessGuard uniquenessGuard;

    public TermActivation(TermRepository termRepository, TermFinder termFinder,
                               ActiveTermUniquenessGuard uniquenessGuard) {
        this.termRepository = termRepository;
        this.termFinder = termFinder;
        this.uniquenessGuard = uniquenessGuard;
    }

    @Transactional
    public void activate(Long id) {
        Term term = termFinder.findOrThrow(id);
        uniquenessGuard.ensureNoOtherActiveTermLike(term);
        term.setStatus(StatusType.ACTIVE);
        termRepository.save(term);
    }
}
