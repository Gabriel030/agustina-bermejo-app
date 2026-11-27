package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.exception.TermNotOpenForOfferingException;
import com.red_escuela.backend.shared.domain.StatusType;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
public class TermOfferingGuard {

    private final Clock clock;

    public TermOfferingGuard(Clock clock) {
        this.clock = clock;
    }

    public void requireTermOpenForOffering(Term term) {
        boolean ended = term.getEndDate() != null && term.getEndDate().isBefore(LocalDateTime.now(clock));
        if (term.getStatus() == StatusType.INACTIVE || ended) {
            throw new TermNotOpenForOfferingException(term.getId());
        }
    }
}
