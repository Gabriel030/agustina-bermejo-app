package com.red_escuela.backend.identity.usecase.auth;

public record ActivateAccountCommand(String token, String newPassword) {
}
