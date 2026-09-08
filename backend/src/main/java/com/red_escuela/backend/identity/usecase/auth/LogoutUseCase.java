package com.red_escuela.backend.identity.usecase.auth;

public interface LogoutUseCase {
    void logout(String rawRefreshToken);
}
