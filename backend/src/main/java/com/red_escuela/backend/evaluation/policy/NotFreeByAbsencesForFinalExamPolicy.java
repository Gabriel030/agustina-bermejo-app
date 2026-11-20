package com.red_escuela.backend.evaluation.policy;

import com.red_escuela.backend.enrollment.api.FinalExamIneligibilityReason;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "STUDENT_FREE_BY_ABSENCES_FOR_FINAL_EXAM", decisions = {"ACAD-2026-11-13-FINAL-REQUIRES-NOT-FREE-BY-ABSENCES"})
public final class NotFreeByAbsencesForFinalExamPolicy implements Policy<FinalExamEligibilityRequest> {
    public String ruleName() {
        return "NotFreeByAbsencesForFinalExamPolicy";
    }

    public PolicyVerdict evaluateRule(FinalExamEligibilityRequest request) {
        return request.eligibility().reason() != FinalExamIneligibilityReason.FREE_BY_ABSENCES
                ? PolicyVerdict.allow(ruleName())
                : PolicyVerdict.deny(ruleName(), "STUDENT_FREE_BY_ABSENCES_FOR_FINAL_EXAM",
                        "Un alumno libre por inasistencias no puede rendir el final hasta justificarlas.");
    }
}
