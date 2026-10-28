package com.red_escuela.backend.billing.collections.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record PostWithdrawalSummaryCandidate(Long studentId, Long personId, List<WithdrawnEnrollmentDebtLine> lines) {

    public PostWithdrawalSummaryCandidate {
        lines = List.copyOf(lines);
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("A post-withdrawal summary candidate needs at least one debt line");
        }
    }

    public static List<PostWithdrawalSummaryCandidate> groupDueOn(List<WithdrawnEnrollmentDebtLine> debtLines, LocalDate businessDate, int summaryMonths,
                                                                  NonBusinessDays nonBusinessDays) {
        Map<Long, List<WithdrawnEnrollmentDebtLine>> dueByStudent = new LinkedHashMap<>();
        for (WithdrawnEnrollmentDebtLine line : debtLines) {
            if (PostWithdrawalSummarySchedule.isSummaryDueOn(line.withdrawalEffectiveDate(), businessDate, summaryMonths, nonBusinessDays)) {
                dueByStudent.computeIfAbsent(line.studentId(), id -> new ArrayList<>()).add(line);
            }
        }
        return dueByStudent.values().stream().map(lines -> new PostWithdrawalSummaryCandidate(lines.get(0).studentId(), lines.get(0).personId(), lines)).toList();
    }

    public List<WithdrawnEnrollmentDebtLine> distinctInvoiceLines() {
        Set<Long> seen = new LinkedHashSet<>();
        return lines.stream().filter(line -> seen.add(line.invoiceId())).toList();
    }
}
