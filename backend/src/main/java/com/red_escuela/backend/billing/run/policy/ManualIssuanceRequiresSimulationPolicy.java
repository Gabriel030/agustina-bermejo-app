package com.red_escuela.backend.billing.run.policy;

import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRun;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunMode;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunStatus;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "MANUAL_ISSUANCE_REQUIRES_SIMULATION", decisions = {"D-055", "D-161"})
public final class ManualIssuanceRequiresSimulationPolicy implements Policy<ManualIssuanceRequiresSimulationContext> {

    @Override
    public String ruleName() {
        return "ManualIssuanceRequiresSimulationPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(ManualIssuanceRequiresSimulationContext context) {
        MonthlyInvoiceRun simulation = context.simulationRun();
        boolean simulatedTodayForThePeriod = simulation != null
                && simulation.getMode() == MonthlyInvoiceRunMode.SIMULATION
                && simulation.getStatus() == MonthlyInvoiceRunStatus.COMPLETED
                && simulation.getPeriod().equals(context.requestedPeriod().atDay(1))
                && simulation.getBusinessDate().equals(context.today());
        return simulatedTodayForThePeriod
                ? PolicyVerdict.allow(ruleName())
                : PolicyVerdict.deny(ruleName(), "MANUAL_ISSUANCE_REQUIRES_SIMULATION", "La emisión manual exige una simulación completa del mismo período hecha hoy");
    }
}
