package com.red_escuela.backend.identity.policy;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-142")
@Tag("D-250")
class StudentWithdrawalRequiresNoActiveEnrollmentsPolicyTest {

    private final StudentWithdrawalRequiresNoActiveEnrollmentsPolicy policy = new StudentWithdrawalRequiresNoActiveEnrollmentsPolicy();

    @Test
    void deniesWhenTheStudentHasActiveEnrollments() {
        var verdict = policy.evaluateRule(new StudentWithdrawalContext(7L, true));

        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.code()).isEqualTo("STUDENT_HAS_ACTIVE_ENROLLMENTS");
    }

    @Test
    void allowsWhenTheStudentHasNoActiveEnrollments() {
        assertThat(policy.evaluateRule(new StudentWithdrawalContext(7L, false)).allowed()).isTrue();
    }
}
