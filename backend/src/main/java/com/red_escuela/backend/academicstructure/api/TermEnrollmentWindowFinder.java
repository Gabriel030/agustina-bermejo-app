package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.exception.TermNotFoundException;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import com.red_escuela.backend.shared.domain.InvalidDateRangeException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@Transactional(readOnly = true)
public class TermEnrollmentWindowFinder {

    private final TermRepository termRepository;

    public TermEnrollmentWindowFinder(TermRepository termRepository) {
        this.termRepository = termRepository;
    }

    public TermEnrollmentWindowView findEnrollmentWindow(Long termId, LocalDateTime moment) {
        Term term = termRepository.findById(termId).orElseThrow(() -> new TermNotFoundException(termId));
        boolean closedByOverride = term.isEnrollmentClosedByOverride();
        try {
            return new TermEnrollmentWindowView(termId, closedByOverride, false, term.isEnrollmentOpenAt(moment));
        } catch (InvalidDateRangeException e) {
            return new TermEnrollmentWindowView(termId, closedByOverride, true, false);
        }
    }
}
