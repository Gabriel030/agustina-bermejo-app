package com.red_escuela.backend.billing.invoice.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"DEBT_IN_PENDING_CANCELLATION_REQUEST", "OUTSTANDING_DEBT_OF_WITHDRAWN_ENROLLMENT", "OVERDUE_INVOICE"},
        decisions = {"D-065", "D-142", "D-314"})
public final class OutstandingDebtBlocksReenrollmentPolicy implements Policy<OutstandingDebtFacts> {

    @Override
    public String ruleName() {
        return "OutstandingDebtBlocksReenrollmentPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(OutstandingDebtFacts facts) {
        if (facts.hasOpenInvoiceOfWithdrawnEnrollmentInPendingRequest()) {
            return PolicyVerdict.deny(ruleName(), "DEBT_IN_PENDING_CANCELLATION_REQUEST",
                    "Hay cuotas de una inscripción dada de baja dentro de un pedido pendiente del director: la vuelta queda frenada hasta que decida");
        }
        if (facts.hasOpenInvoiceOfWithdrawnEnrollment()) {
            return PolicyVerdict.deny(ruleName(), "OUTSTANDING_DEBT_OF_WITHDRAWN_ENROLLMENT",
                    "Hay deuda de una inscripción dada de baja: hay que pagarla antes de volver a inscribirse");
        }
        if (facts.hasOverdueInvoice()) {
            return PolicyVerdict.deny(ruleName(), "OVERDUE_INVOICE",
                    "Hay una factura vencida en alguna inscripción: hay que regularizarla antes de inscribirse a algo nuevo");
        }
        return PolicyVerdict.allow(ruleName());
    }
}
