package com.red_escuela.backend.evaluation.policy;

import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = "STUDENT_NOT_REGULAR_FOR_FINAL_EXAM", decisions = {"ACAD-2026-10-07-FINAL-REQUIRES-REGULAR"})
public final class RegularStudentForFinalExamPolicy implements Policy<FinalExamLoadRequest> {
    public String name() {
        return "RegularStudentForFinalExamPolicy";
    }

    public PolicyVerdict evaluate(FinalExamLoadRequest request) {
        return request.enrollmentStatus() == EnrollmentStatus.REGULAR
                ? PolicyVerdict.allow(name())
                : PolicyVerdict.deny(name(), "STUDENT_NOT_REGULAR_FOR_FINAL_EXAM",
                        "Solo un alumno regular en la materia puede rendir el final.");
    }
}
