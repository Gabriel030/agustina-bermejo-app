package com.red_escuela.backend.billing.api;

import com.red_escuela.backend.billing.collections.service.StudentDebtSummaryFinder;
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

    private final StudentDebtSummaryFinder finder;
    private final Clock clock;

    public StudentDebtSummariesQuery(StudentDebtSummaryFinder finder, Clock clock) {
        this.finder = finder;
        this.clock = clock;
    }

    public Map<Long, StudentDebtSummary> summariesOf(Collection<Long> studentIds) {
        Map<Long, StudentDebtSummary> summaries = new HashMap<>();
        finder.findByStudentIds(studentIds, LocalDate.now(clock)).forEach(row -> summaries.put(row.studentId(),
                new StudentDebtSummary(Money.of(row.totalDue(), Currency.ARS), row.installments(), row.overdueInstallments())));
        return summaries;
    }
}
