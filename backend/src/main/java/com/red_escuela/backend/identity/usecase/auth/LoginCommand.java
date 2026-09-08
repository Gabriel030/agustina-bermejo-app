package com.red_escuela.backend.identity.usecase.auth;

public record LoginCommand(String username, String password, String ipAddress) {
}
