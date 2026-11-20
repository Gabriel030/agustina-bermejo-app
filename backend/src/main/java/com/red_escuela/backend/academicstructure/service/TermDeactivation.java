package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.api.SectionEnrollmentActivityPort;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.exception.TermHasActiveEnrollmentsException;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import com.red_escuela.backend.shared.domain.StatusType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TermDeactivation {

    private final TermRepository termRepository;
    private final TermFinder termFinder;
    private final SectionEnrollmentActivityPort enrollmentActivityPort;

    public TermDeactivation(TermRepository termRepository, TermFinder termFinder,
            SectionEnrollmentActivityPort enrollmentActivityPort) {
        this.enrollmentActivityPort = enrollmentActivityPort;
        this.termRepository = termRepository;
        this.termFinder = termFinder;
    }

    @Transactional
    public void deactivate(Long id) {
        Term term = termFinder.findOrThrow(id);
        if (enrollmentActivityPort.hasLiveEnrollmentsInTerm(id)) {
            throw new TermHasActiveEnrollmentsException(id);
        }
        term.setStatus(StatusType.INACTIVE);
        termRepository.save(term);
    }
}
