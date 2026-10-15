package com.red_escuela.backend.identity.domain;

import com.red_escuela.backend.identity.api.StudentEnrollmentActivityLookup;
import com.red_escuela.backend.identity.exception.StudentHasActiveEnrollmentsException;
import com.red_escuela.backend.identity.policy.StudentWithdrawalContext;
import com.red_escuela.backend.identity.policy.StudentWithdrawalRequiresNoActiveEnrollmentsPolicy;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StudentWithdrawalGuard {

    private static final PolicySet<StudentWithdrawalContext> STUDENT_WITHDRAWAL_RULES =
            PolicySet.named("STUDENT_WITHDRAWAL", List.of(new StudentWithdrawalRequiresNoActiveEnrollmentsPolicy()));

    private final StudentEnrollmentActivityLookup enrollmentActivityLookup;
    private final StudentRepository studentRepository;

    public StudentWithdrawalGuard(StudentEnrollmentActivityLookup enrollmentActivityLookup, StudentRepository studentRepository) {
        this.enrollmentActivityLookup = enrollmentActivityLookup;
        this.studentRepository = studentRepository;
    }

    public void requireStudentWithdrawable(Long studentId) {
        studentRepository.findByIdForUpdate(studentId);
        StudentWithdrawalContext context = new StudentWithdrawalContext(studentId, enrollmentActivityLookup.hasActiveEnrollments(studentId));
        STUDENT_WITHDRAWAL_RULES.evaluateAllRules(context).throwIfDenied(StudentHasActiveEnrollmentsException::new);
    }
}
