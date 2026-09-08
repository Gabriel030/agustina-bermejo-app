package com.red_escuela.backend.identity.usecase.auth;

public interface RequestPasswordResetUseCase {
    void requestReset(String email);
}
