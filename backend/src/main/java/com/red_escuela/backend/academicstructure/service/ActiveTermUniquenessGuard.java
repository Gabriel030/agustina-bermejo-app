package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.domain.TermType;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.exception.ActiveTermAlreadyExistsException;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import com.red_escuela.backend.shared.domain.StatusType;
import org.springframework.stereotype.Service;

@Service
public class ActiveTermUniquenessGuard {

    private final TermRepository termRepository;

    public ActiveTermUniquenessGuard(TermRepository termRepository) {
        this.termRepository = termRepository;
    }

    public void ensureNoOtherActiveTermLike(Term term) {
        ensureNoOtherActiveTerm(term.getYear(), term.getType(), term.getId());
    }

    public void ensureNoOtherActiveTerm(Integer year, TermType type, Long ignoredTermId) {
        if (year == null || type == null) {
            return;
        }
        boolean exists = ignoredTermId == null
                ? termRepository.existsByYearAndTypeAndStatus(year, type, StatusType.ACTIVE)
                : termRepository.existsByYearAndTypeAndStatusAndIdNot(year, type, StatusType.ACTIVE, ignoredTermId);
        if (exists) {
            throw new ActiveTermAlreadyExistsException(year, type);
        }
    }
}
