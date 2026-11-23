package com.red_escuela.backend.billing.api;

import com.red_escuela.backend.billing.collections.service.StudentOverdueDebtFinder;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Component
@Transactional(readOnly = true)
public class StudentDebtSummariesQuery {

    private final StudentOverdueDebtFinder overdueDebtFinder;
    private final Clock clock;

    public StudentDebtSummariesQuery(StudentOverdueDebtFinder overdueDebtFinder, Clock clock) {
        this.overdueDebtFinder = overdueDebtFinder;
        this.clock = clock;
    }

    public Map<Long, StudentDebtSummary> summariesOf(Collection<Long> studentIds) {
        Map<Long, StudentDebtSummary> summaries = new HashMap<>();
        overdueDebtFinder.summarizeArsOverdueDebt(studentIds, LocalDate.now(clock)).forEach((studentId, totals) -> summaries.put(studentId,
                new StudentDebtSummary(Money.of(totals.totalDue(), Currency.ARS), totals.overdueInstallments())));
        return summaries;
    }
}
