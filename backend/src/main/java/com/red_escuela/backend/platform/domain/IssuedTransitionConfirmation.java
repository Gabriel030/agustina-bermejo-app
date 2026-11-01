package com.red_escuela.backend.platform.domain;

import java.time.LocalDateTime;

public record IssuedTransitionConfirmation(String token, LocalDateTime expiresAt) {
}
