package com.red_escuela.backend.identity.usecase.emailverification;

import com.red_escuela.backend.identity.service.EmailVerificationConfirmation;
import com.red_escuela.backend.identity.util.TokenHashUtil;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class VerifyEmailService implements VerifyEmailUseCase {

    private final EmailVerificationConfirmation emailVerificationConfirmation;

    public VerifyEmailService(EmailVerificationConfirmation emailVerificationConfirmation) {
        this.emailVerificationConfirmation = emailVerificationConfirmation;
    }

    @Override
    public void verify(String clearToken) {
        emailVerificationConfirmation.confirmByTokenHash(TokenHashUtil.sha256Hex(clearToken));
    }
}
