package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.api.TermPeriodFinder;
import com.red_escuela.backend.academicstructure.api.TermPeriodView;
import com.red_escuela.backend.enrollment.exception.SectionWithdrawalTermEndedException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
public class StudentWithdrawalTermGuard {

    private final TermPeriodFinder termPeriodFinder;
    private final Clock clock;

    public StudentWithdrawalTermGuard(TermPeriodFinder termPeriodFinder, Clock clock) {
        this.termPeriodFinder = termPeriodFinder;
        this.clock = clock;
    }

    public void requireTermNotEnded(EnrollmentChange change, Long termId) {
        if (change.madeByStaff()) {
            return;
        }
        TermPeriodView period = termPeriodFinder.findPeriod(termId);
        if (period.endDate() != null && LocalDateTime.now(clock).isAfter(period.endDate())) {
            throw new SectionWithdrawalTermEndedException();
        }
    }
}
