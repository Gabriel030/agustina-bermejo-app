package com.red_escuela.backend.identity.policy;

import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicyRule;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;

@PolicyRule(violationCodes = {"STUDENT_HAS_ACTIVE_ENROLLMENTS"}, decisions = {"D-142", "D-250"})
public final class StudentWithdrawalRequiresNoActiveEnrollmentsPolicy implements Policy<StudentWithdrawalContext> {

    @Override
    public String ruleName() {
        return "StudentWithdrawalRequiresNoActiveEnrollmentsPolicy";
    }

    @Override
    public PolicyVerdict evaluateRule(StudentWithdrawalContext context) {
        if (context.hasActiveEnrollments()) {
            return PolicyVerdict.deny(ruleName(), "STUDENT_HAS_ACTIVE_ENROLLMENTS",
                    "Primero hay que dar de baja cada inscripción activa del alumno");
        }
        return PolicyVerdict.allow(ruleName());
    }
}
