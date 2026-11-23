package com.red_escuela.backend.identity.usecase.auth;

import com.red_escuela.backend.identity.service.AccountActivationTransaction;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ActivateAccountService implements ActivateAccountUseCase {
    private final AccountActivationTransaction accountActivationTransaction;

    public ActivateAccountService(AccountActivationTransaction accountActivationTransaction) {
        this.accountActivationTransaction = accountActivationTransaction;
    }

    @Override
    public void activate(ActivateAccountCommand command) {
        accountActivationTransaction.activate(command.token(), command.newPassword());
    }
}
