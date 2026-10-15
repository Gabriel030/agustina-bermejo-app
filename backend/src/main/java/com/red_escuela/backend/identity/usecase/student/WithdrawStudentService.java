package com.red_escuela.backend.identity.usecase.student;

import com.red_escuela.backend.identity.api.StudentEnrollmentActivityLookup;
import com.red_escuela.backend.identity.exception.StudentHasActiveEnrollmentsException;
import com.red_escuela.backend.identity.policy.StudentWithdrawalContext;
import com.red_escuela.backend.identity.policy.StudentWithdrawalRequiresNoActiveEnrollmentsPolicy;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.exception.StudentNotFoundException;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.identity.usecase.person.RevokeAllSessionsUseCase;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@UseCase
public class WithdrawStudentService implements WithdrawStudentUseCase {

    private static final PolicySet<StudentWithdrawalContext> STUDENT_WITHDRAWAL_RULES =
            PolicySet.named("STUDENT_WITHDRAWAL", List.of(new StudentWithdrawalRequiresNoActiveEnrollmentsPolicy()));

    private final StudentRepository studentRepository;
    private final RevokeAllSessionsUseCase revokeAllSessionsUseCase;
    private final StudentEnrollmentActivityLookup enrollmentActivityLookup;

    public WithdrawStudentService(StudentRepository studentRepository, RevokeAllSessionsUseCase revokeAllSessionsUseCase,
                                  StudentEnrollmentActivityLookup enrollmentActivityLookup) {
        this.studentRepository = studentRepository;
        this.revokeAllSessionsUseCase = revokeAllSessionsUseCase;
        this.enrollmentActivityLookup = enrollmentActivityLookup;
    }

    @Override
    @Transactional
    public void withdraw(WithdrawStudentCommand command) {
        Student student = studentRepository.findById(command.studentId())
                .orElseThrow(() -> new StudentNotFoundException(command.studentId()));
        StudentWithdrawalContext context = new StudentWithdrawalContext(command.studentId(), enrollmentActivityLookup.hasActiveEnrollments(command.studentId()));
        STUDENT_WITHDRAWAL_RULES.evaluateAllRules(context).throwIfDenied(StudentHasActiveEnrollmentsException::new);
        student.getPerson().setStatus(StatusType.DELETED);
        student.setWithdrawalReason(command.request().reason());
        student.setWithdrawalDetail(command.request().detail());
        studentRepository.save(student);
        revokeAllSessionsUseCase.revokeAll(student.getPerson());
    }
}
