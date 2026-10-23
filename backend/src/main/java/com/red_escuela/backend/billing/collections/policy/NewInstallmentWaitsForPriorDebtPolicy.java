package com.red_escuela.backend.billing.collections.policy;

import com.red_escuela.backend.billing.collections.domain.ReminderPriorDebt;
import com.red_escuela.backend.billing.configuration.domain.ReminderMessageType;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

import java.util.Set;

@PolicyRule(violationCodes = "NEW_INSTALLMENT_WAITS_FOR_PRIOR_DEBT", decisions = {"D-347"})
public final class NewInstallmentWaitsForPriorDebtPolicy implements Policy<PriorDebtReminderContext> {
    private static final Set<ReminderMessageType> OWN_CADENCE_TYPES = Set.of(ReminderMessageType.AFTER_ISSUE_REMINDER, ReminderMessageType.BEFORE_DUE_REMINDER);

    @Override
    public String ruleName() {
        return "NewInstallmentWaitsForPriorDebtPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(PriorDebtReminderContext context) {
        if (OWN_CADENCE_TYPES.contains(context.messageType()) && ReminderPriorDebt.existsBefore(context.invoice(), context.listedDebt())) {
            return PolicyVerdict.deny(ruleName(), "NEW_INSTALLMENT_WAITS_FOR_PRIOR_DEBT",
                    "La cuota nueva no tiene avisos propios mientras exista una cuota anterior impaga: el resumen de deuda cubre todo");
        }
        return PolicyVerdict.allow(ruleName());
    }
}
