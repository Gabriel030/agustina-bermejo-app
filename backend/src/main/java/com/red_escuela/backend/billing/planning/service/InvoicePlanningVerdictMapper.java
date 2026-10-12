package com.red_escuela.backend.billing.planning.service;

import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.planning.domain.InvoicePlanningDecision;
import com.red_escuela.backend.billing.planning.policy.InvoicePlanningPolicy;
import com.red_escuela.backend.shared.domain.policy.PolicyEvaluationResult;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

import java.util.Optional;

public final class InvoicePlanningVerdictMapper {
    private static final int ELIGIBILITY_QUESTION = 1;

    public InvoicePlanningDecision mapViolationToPlanningDecision(InvoicePlanningPolicy<?> policy, PolicyVerdict verdict) {
        return new InvoicePlanningDecision(policy.plannerQuestionNumber(), verdict.code(), verdict.reason());
    }

    public <C> Optional<InvoicePlanningDecision> mapFirstViolationToPlanningDecision(PolicySet<C> policySet, PolicyEvaluationResult result) {
        return result.firstDenial().map(verdict -> mapViolationToPlanningDecision(policyNamed(policySet, verdict.policy()), verdict));
    }

    public InvoicePlanningDecision mapEligibleToPlanningDecision(PaymentPlanCadence cadence) {
        return new InvoicePlanningDecision(ELIGIBILITY_QUESTION, "ELIGIBLE", "plan de pago " + cadence);
    }

    private <C> InvoicePlanningPolicy<?> policyNamed(PolicySet<C> policySet, String ruleName) {
        return policySet.policies().stream().filter(policy -> policy.ruleName().equals(ruleName)).findFirst()
                .map(policy -> (InvoicePlanningPolicy<?>) policy).orElseThrow();
    }
}
