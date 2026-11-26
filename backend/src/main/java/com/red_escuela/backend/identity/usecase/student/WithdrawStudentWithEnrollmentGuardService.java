package com.red_escuela.backend.identity.usecase.student;

import com.red_escuela.backend.identity.service.WithdrawStudentWithGuardTransaction;
import com.red_escuela.backend.identity.specialist.StudentOnlyTargetPolicy;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.context.annotation.Primary;

@UseCase
@Primary
public class WithdrawStudentWithEnrollmentGuardService implements WithdrawStudentUseCase {
    private final WithdrawStudentWithGuardTransaction withdrawStudentWithGuardTransaction;
    private final StudentOnlyTargetPolicy studentOnlyTargetPolicy;

    public WithdrawStudentWithEnrollmentGuardService(WithdrawStudentWithGuardTransaction withdrawStudentWithGuardTransaction, StudentOnlyTargetPolicy studentOnlyTargetPolicy) {
        this.withdrawStudentWithGuardTransaction = withdrawStudentWithGuardTransaction;
        this.studentOnlyTargetPolicy = studentOnlyTargetPolicy;
    }

    @Override
    public void withdraw(WithdrawStudentCommand command) {
        studentOnlyTargetPolicy.requireStudentOnly(command.studentId());
        withdrawStudentWithGuardTransaction.withdraw(command.studentId(), command.request());
    }
}
