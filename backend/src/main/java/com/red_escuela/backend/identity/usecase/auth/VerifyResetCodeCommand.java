package com.red_escuela.backend.identity.usecase.auth;

public record VerifyResetCodeCommand(String email, String code) {
}
