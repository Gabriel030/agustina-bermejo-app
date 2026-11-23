package com.red_escuela.backend.identity.usecase.staff;

import com.red_escuela.backend.identity.service.WithdrawStaffTransaction;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class WithdrawStaffService implements WithdrawStaffUseCase {
    private final WithdrawStaffTransaction withdrawStaffTransaction;

    public WithdrawStaffService(WithdrawStaffTransaction withdrawStaffTransaction) {
        this.withdrawStaffTransaction = withdrawStaffTransaction;
    }

    @Override
    public void withdraw(Long personId) {
        withdrawStaffTransaction.withdraw(personId);
    }
}
