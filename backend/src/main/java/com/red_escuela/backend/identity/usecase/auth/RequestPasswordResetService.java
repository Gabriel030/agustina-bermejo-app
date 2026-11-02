package com.red_escuela.backend.identity.usecase.auth;

import com.red_escuela.backend.identity.service.PasswordResetCodeRequester;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class RequestPasswordResetService implements RequestPasswordResetUseCase {
    private final PasswordResetCodeRequester passwordResetCodeRequester;

    public RequestPasswordResetService(PasswordResetCodeRequester passwordResetCodeRequester) {
        this.passwordResetCodeRequester = passwordResetCodeRequester;
    }

    @Override
    public void requestReset(String email) {
        passwordResetCodeRequester.requestPasswordResetCode(email);
    }
}
