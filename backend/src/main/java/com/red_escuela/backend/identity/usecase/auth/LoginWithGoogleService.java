package com.red_escuela.backend.identity.usecase.auth;

import com.red_escuela.backend.identity.dto.AuthTokens;
import com.red_escuela.backend.identity.service.GoogleLoginTransaction;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class LoginWithGoogleService implements LoginWithGoogleUseCase {
    private final GoogleLoginTransaction googleLoginTransaction;

    public LoginWithGoogleService(GoogleLoginTransaction googleLoginTransaction) {
        this.googleLoginTransaction = googleLoginTransaction;
    }

    @Override
    public AuthTokens login(String idToken) {
        return googleLoginTransaction.login(idToken);
    }
}
