package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.exception.TermNotFoundException;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class TermPeriodFinder {

    private final TermRepository termRepository;

    public TermPeriodFinder(TermRepository termRepository) {
        this.termRepository = termRepository;
    }

    public TermPeriodView findPeriod(Long termId) {
        Term term = termRepository.findById(termId).orElseThrow(() -> new TermNotFoundException(termId));
        return new TermPeriodView(term.getId(), term.getStartDate(), term.getEndDate());
    }
}
