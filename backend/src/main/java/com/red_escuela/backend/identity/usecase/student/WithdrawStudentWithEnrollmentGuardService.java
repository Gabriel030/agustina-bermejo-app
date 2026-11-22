package com.red_escuela.backend.identity.usecase.student;

import com.red_escuela.backend.identity.service.WithdrawStudentWithGuardTransaction;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.context.annotation.Primary;

@UseCase
@Primary
public class WithdrawStudentWithEnrollmentGuardService implements WithdrawStudentUseCase {
    private final WithdrawStudentWithGuardTransaction withdrawStudentWithGuardTransaction;

    public WithdrawStudentWithEnrollmentGuardService(WithdrawStudentWithGuardTransaction withdrawStudentWithGuardTransaction) {
        this.withdrawStudentWithGuardTransaction = withdrawStudentWithGuardTransaction;
    }

    @Override
    public void withdraw(WithdrawStudentCommand command) {
        withdrawStudentWithGuardTransaction.withdraw(command.studentId(), command.request());
    }
}
