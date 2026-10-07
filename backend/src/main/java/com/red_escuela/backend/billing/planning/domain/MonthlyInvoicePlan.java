package com.red_escuela.backend.billing.planning.domain;

import java.time.YearMonth;
import java.util.List;
import com.red_escuela.backend.billing.invoice.domain.FeeType;

public record MonthlyInvoicePlan(Long enrollmentId, YearMonth month, List<PlannedInvoice> charges, List<InvoicePlanningDecision> decisions, boolean notConfigured) {
    public boolean has(FeeType kind) {
        return charges.stream().anyMatch(c -> c.kind() == kind);
    }

    public String describe() {
        StringBuilder sb = new StringBuilder();
        for (InvoicePlanningDecision d : decisions) {
            sb.append(" [P").append(d.question()).append(' ').append(d.code());
            if (d.detail() != null) {
                sb.append(": ").append(d.detail());
            }
            sb.append(']');
        }
        return sb.toString();
    }
}
