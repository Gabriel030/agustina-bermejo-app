package com.red_escuela.backend.identity.usecase.auth;

public interface VerifyResetCodeUseCase {
    String verify(VerifyResetCodeCommand command);
}
