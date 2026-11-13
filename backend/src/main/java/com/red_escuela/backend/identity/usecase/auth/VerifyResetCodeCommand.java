package com.red_escuela.backend.identity.usecase.auth;

public record VerifyResetCodeCommand(String email, String code, String ipAddress) {

    public VerifyResetCodeCommand(String email, String code) {
        this(email, code, null);
    }
}
