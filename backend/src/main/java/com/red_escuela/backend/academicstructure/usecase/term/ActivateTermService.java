package com.red_escuela.backend.academicstructure.usecase.term;

import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import com.red_escuela.backend.academicstructure.service.ActiveTermUniquenessGuard;
import com.red_escuela.backend.academicstructure.service.TermFinder;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class ActivateTermService implements ActivateTermUseCase {

    private final TermRepository termRepository;
    private final TermFinder termFinder;
    private final ActiveTermUniquenessGuard uniquenessGuard;

    public ActivateTermService(TermRepository termRepository, TermFinder termFinder,
                               ActiveTermUniquenessGuard uniquenessGuard) {
        this.termRepository = termRepository;
        this.termFinder = termFinder;
        this.uniquenessGuard = uniquenessGuard;
    }

    @Override
    @Transactional
    public void activate(Long id) {
        Term term = termFinder.findOrThrow(id);
        uniquenessGuard.ensureNoOtherActiveTermLike(term);
        term.setStatus(StatusType.ACTIVE);
        termRepository.save(term);
    }
}
