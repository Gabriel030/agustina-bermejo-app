package com.red_escuela.backend.billing.planning;

import java.time.YearMonth;
import java.util.List;

public record ChargePlan(Long enrollmentId, YearMonth month, List<PlannedCharge> charges, List<PlanDecision> decisions, boolean notConfigured) {
    public boolean has(ChargeKind kind) {
        return charges.stream().anyMatch(c -> c.kind() == kind);
    }

    public String describe() {
        StringBuilder sb = new StringBuilder();
        for (PlanDecision d : decisions) {
            sb.append(" [P").append(d.question()).append(' ').append(d.code());
            if (d.detail() != null) {
                sb.append(": ").append(d.detail());
            }
            sb.append(']');
        }
        return sb.toString();
    }
}
