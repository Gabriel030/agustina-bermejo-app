package com.red_escuela.backend.identity.usecase.student;

import com.red_escuela.backend.identity.service.WithdrawStudentTransaction;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class WithdrawStudentService implements WithdrawStudentUseCase {
    private final WithdrawStudentTransaction withdrawStudentTransaction;

    public WithdrawStudentService(WithdrawStudentTransaction withdrawStudentTransaction) {
        this.withdrawStudentTransaction = withdrawStudentTransaction;
    }

    @Override
    public void withdraw(WithdrawStudentCommand command) {
        withdrawStudentTransaction.withdraw(command.studentId(), command.request());
    }
}
