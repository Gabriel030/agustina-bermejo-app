package com.red_escuela.backend.billing.api;

import com.red_escuela.backend.billing.collections.service.StudentOverdueDebtFinder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

@Component
@Transactional(readOnly = true)
public class StudentOverdueDebtQuery {

    private final StudentOverdueDebtFinder overdueDebtFinder;
    private final Clock clock;

    public StudentOverdueDebtQuery(StudentOverdueDebtFinder overdueDebtFinder, Clock clock) {
        this.overdueDebtFinder = overdueDebtFinder;
        this.clock = clock;
    }

    public boolean hasOverdueDebt(Long studentId) {
        return overdueDebtFinder.hasOverdueDebt(studentId, LocalDate.now(clock));
    }
}
