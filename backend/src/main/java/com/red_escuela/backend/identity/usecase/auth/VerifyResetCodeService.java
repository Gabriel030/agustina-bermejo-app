package com.red_escuela.backend.identity.usecase.auth;

import com.red_escuela.backend.identity.service.ResetCodeVerificationTransaction;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class VerifyResetCodeService implements VerifyResetCodeUseCase {
    private final ResetCodeVerificationTransaction resetCodeVerificationTransaction;

    public VerifyResetCodeService(ResetCodeVerificationTransaction resetCodeVerificationTransaction) {
        this.resetCodeVerificationTransaction = resetCodeVerificationTransaction;
    }

    @Override
    public String verify(VerifyResetCodeCommand command) {
        return resetCodeVerificationTransaction.verify(command.email(), command.code(), command.ipAddress());
    }
}
