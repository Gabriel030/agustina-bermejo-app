package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.entity.TermEnrollmentOverride;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TermEnrollmentOpening {

    private final TermRepository termRepository;
    private final TermFinder termFinder;

    public TermEnrollmentOpening(TermRepository termRepository, TermFinder termFinder) {
        this.termRepository = termRepository;
        this.termFinder = termFinder;
    }

    @Transactional
    public void open(Long id) {
        Term term = termFinder.findOrThrow(id);
        term.setEnrollmentOverride(TermEnrollmentOverride.OPEN);
        termRepository.save(term);
    }
}
