package com.red_escuela.backend.identity.usecase.auth;

import com.red_escuela.backend.identity.service.PasswordResetTransaction;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ResetPasswordService implements ResetPasswordUseCase {
    private final PasswordResetTransaction passwordResetTransaction;

    public ResetPasswordService(PasswordResetTransaction passwordResetTransaction) {
        this.passwordResetTransaction = passwordResetTransaction;
    }

    @Override
    public void reset(ResetPasswordCommand command) {
        passwordResetTransaction.reset(command.exchangeToken(), command.newPassword());
    }
}
