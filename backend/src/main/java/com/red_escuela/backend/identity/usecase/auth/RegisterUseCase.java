package com.red_escuela.backend.identity.usecase.auth;

import com.red_escuela.backend.identity.dto.RegisterRequest;

public interface RegisterUseCase {
    AuthTokens register(RegisterRequest request);
}
