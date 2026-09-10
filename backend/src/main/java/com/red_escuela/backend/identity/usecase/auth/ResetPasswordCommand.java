package com.red_escuela.backend.identity.usecase.auth;

public record ResetPasswordCommand(String exchangeToken, String newPassword) {
}
