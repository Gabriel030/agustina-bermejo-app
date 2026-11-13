package com.red_escuela.backend.evaluation.policy;

import com.red_escuela.backend.enrollment.api.FinalExamIneligibilityReason;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "STUDENT_NOT_REGULAR_FOR_FINAL_EXAM", decisions = {"ACAD-2026-10-07-FINAL-REQUIRES-REGULAR"})
public final class RegularStudentForFinalExamPolicy implements Policy<FinalExamEligibilityRequest> {
    public String ruleName() {
        return "RegularStudentForFinalExamPolicy";
    }

    public PolicyVerdict evaluateRule(FinalExamEligibilityRequest request) {
        return request.eligibility().reason() != FinalExamIneligibilityReason.COURSEWORK_NOT_REGULAR
                ? PolicyVerdict.allow(ruleName())
                : PolicyVerdict.deny(ruleName(), "STUDENT_NOT_REGULAR_FOR_FINAL_EXAM",
                        "Solo un alumno regular en la materia puede rendir el final.");
    }
}
