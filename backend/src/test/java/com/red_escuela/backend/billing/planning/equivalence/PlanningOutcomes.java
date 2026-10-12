package com.red_escuela.backend.billing.planning.equivalence;

import com.red_escuela.backend.billing.planning.domain.EnrollmentFeeRequirementScope;
import com.red_escuela.backend.billing.planning.domain.ProgramInvoicePlanningFacts;
import com.red_escuela.backend.billing.planning.domain.YearCycle;
import com.red_escuela.backend.billing.planning.policy.EnrollmentFeeRequirementContext;
import com.red_escuela.backend.billing.planning.policy.PlanningPolicySetsConfiguration;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

import java.util.ArrayList;
import java.util.List;

public final class PlanningOutcomes {
    private static final PlanningPolicySetsConfiguration SETS = new PlanningPolicySetsConfiguration();

    private PlanningOutcomes() {
    }

    public static String requirementOutcome(ProgramInvoicePlanningFacts facts, YearCycle cycle, int slot, EnrollmentFeeRequirementScope scope, boolean exempted) {
        EnrollmentFeeRequirementContext context = new EnrollmentFeeRequirementContext(scope, facts, cycle, slot, cycle.terms(), exempted, facts.feeOf(cycle, slot));
        return SETS.enrollmentFeeRequirementApplicability().evaluateRulesUntilFirstViolation(context).firstDenial()
                .map(denial -> "NOT_REQUIRED")
                .orElseGet(() -> SETS.enrollmentFeeRequirement().evaluateRulesUntilFirstViolation(context).firstDenial()
                        .map(PlanningOutcomes::requirementViolation).orElse("SETTLED"));
    }

    private static String requirementViolation(PolicyVerdict denial) {
        return denial.code().equals("ENROLLMENT_FEE_NOT_ISSUED") ? "NOT_ISSUED" : "NOT_PAID";
    }

    public static List<String> requirementOutcomes(ProgramInvoicePlanningFacts facts) {
        List<String> outcomes = new ArrayList<>();
        for (YearCycle cycle : facts.cycles().all()) {
            for (int slot = 1; slot <= cycle.slotCount(); slot++) {
                for (EnrollmentFeeRequirementScope scope : EnrollmentFeeRequirementScope.values()) {
                    for (boolean exempted : new boolean[]{false, true}) {
                        outcomes.add(requirementOutcome(facts, cycle, slot, scope, exempted));
                    }
                }
            }
        }
        return outcomes;
    }
}
