package com.red_escuela.backend.identity.usecase.auth;

import com.red_escuela.backend.identity.dto.AuthTokens;

public interface LoginUseCase {
    AuthTokens login(LoginCommand command);
}
