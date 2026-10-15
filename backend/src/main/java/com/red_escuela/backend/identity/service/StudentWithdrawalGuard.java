package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.identity.api.StudentEnrollmentActivityLookup;
import com.red_escuela.backend.identity.exception.StudentHasActiveEnrollmentsException;
import com.red_escuela.backend.identity.policy.StudentWithdrawalContext;
import com.red_escuela.backend.identity.policy.StudentWithdrawalRequiresNoActiveEnrollmentsPolicy;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class StudentWithdrawalGuard {

    private static final PolicySet<StudentWithdrawalContext> STUDENT_WITHDRAWAL_RULES =
            PolicySet.named("STUDENT_WITHDRAWAL", List.of(new StudentWithdrawalRequiresNoActiveEnrollmentsPolicy()));

    private final StudentEnrollmentActivityLookup enrollmentActivityLookup;

    public StudentWithdrawalGuard(StudentEnrollmentActivityLookup enrollmentActivityLookup) {
        this.enrollmentActivityLookup = enrollmentActivityLookup;
    }

    @Transactional(readOnly = true)
    public void requireStudentWithdrawable(Long studentId) {
        StudentWithdrawalContext context = new StudentWithdrawalContext(studentId, enrollmentActivityLookup.hasActiveEnrollments(studentId));
        STUDENT_WITHDRAWAL_RULES.evaluateAllRules(context).throwIfDenied(StudentHasActiveEnrollmentsException::new);
    }
}
