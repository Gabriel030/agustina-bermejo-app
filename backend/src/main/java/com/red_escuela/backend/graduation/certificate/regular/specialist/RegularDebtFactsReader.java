package com.red_escuela.backend.graduation.certificate.regular.specialist;

import com.red_escuela.backend.billing.api.StudentDebtSummariesQuery;
import com.red_escuela.backend.billing.api.StudentDebtSummary;
import com.red_escuela.backend.platform.api.BillingModeGate;
import com.red_escuela.backend.shared.module.BillingMode;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Service
public class RegularDebtFactsReader {

    private final BillingModeGate billingModeGate;
    private final StudentDebtSummariesQuery summariesQuery;

    public RegularDebtFactsReader(BillingModeGate billingModeGate, StudentDebtSummariesQuery summariesQuery) {
        this.billingModeGate = billingModeGate;
        this.summariesQuery = summariesQuery;
    }

    public boolean tracksDebt() {
        return billingModeGate.allows(BillingMode.MIRROR);
    }

    public Map<Long, RegularDebtFacts> factsOf(Collection<Long> studentIds) {
        Map<Long, RegularDebtFacts> facts = new HashMap<>();
        if (!tracksDebt() || studentIds.isEmpty()) {
            return facts;
        }
        Map<Long, StudentDebtSummary> summaries = summariesQuery.summariesOf(studentIds);
        for (Long studentId : studentIds) {
            StudentDebtSummary summary = summaries.getOrDefault(studentId, StudentDebtSummary.none());
            facts.put(studentId, new RegularDebtFacts(summary.totalDue(), summary.overdueInstallments(), summary.hasDebt()));
        }
        return facts;
    }
}
