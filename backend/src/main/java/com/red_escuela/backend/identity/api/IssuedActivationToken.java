package com.red_escuela.backend.identity.api;

import java.time.LocalDateTime;

public record IssuedActivationToken(String token, LocalDateTime expiresAt) {
}
