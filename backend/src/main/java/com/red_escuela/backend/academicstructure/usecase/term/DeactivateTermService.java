package com.red_escuela.backend.academicstructure.usecase.term;

import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import com.red_escuela.backend.academicstructure.service.TermFinder;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class DeactivateTermService implements DeactivateTermUseCase {

    private final TermRepository termRepository;
    private final TermFinder termFinder;

    public DeactivateTermService(TermRepository termRepository, TermFinder termFinder) {
        this.termRepository = termRepository;
        this.termFinder = termFinder;
    }

    @Override
    @Transactional
    public void deactivate(Long id) {
        Term term = termFinder.findOrThrow(id);
        term.setStatus(StatusType.INACTIVE);
        termRepository.save(term);
    }
}
