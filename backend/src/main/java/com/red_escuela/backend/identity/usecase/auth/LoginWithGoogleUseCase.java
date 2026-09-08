package com.red_escuela.backend.identity.usecase.auth;

public interface LoginWithGoogleUseCase {
    AuthTokens login(String idToken);
}
