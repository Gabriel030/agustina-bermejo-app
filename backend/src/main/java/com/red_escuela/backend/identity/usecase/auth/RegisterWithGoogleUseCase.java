package com.red_escuela.backend.identity.usecase.auth;

public interface RegisterWithGoogleUseCase {
    AuthTokens register(RegisterWithGoogleCommand command);
}
