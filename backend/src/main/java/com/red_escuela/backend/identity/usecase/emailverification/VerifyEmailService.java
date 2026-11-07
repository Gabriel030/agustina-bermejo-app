package com.red_escuela.backend.identity.usecase.emailverification;

import com.red_escuela.backend.identity.specialist.EmailVerificationConfirmer;
import com.red_escuela.backend.identity.util.TokenHashUtil;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class VerifyEmailService implements VerifyEmailUseCase {

    private final EmailVerificationConfirmer emailVerificationConfirmation;

    public VerifyEmailService(EmailVerificationConfirmer emailVerificationConfirmation) {
        this.emailVerificationConfirmation = emailVerificationConfirmation;
    }

    @Override
    public void verify(String clearToken) {
        emailVerificationConfirmation.confirmByTokenHash(TokenHashUtil.sha256Hex(clearToken));
    }
}
