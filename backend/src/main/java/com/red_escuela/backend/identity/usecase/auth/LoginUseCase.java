package com.red_escuela.backend.identity.usecase.auth;

public interface LoginUseCase {
    AuthTokens login(LoginCommand command);
}
