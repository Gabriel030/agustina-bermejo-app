package com.red_escuela.backend.identity.dto;

import com.red_escuela.backend.identity.entity.RefreshToken;

import java.time.LocalDateTime;

public record SessionResponse(Long id, LocalDateTime createdAt, LocalDateTime expiresAt) {

    public static SessionResponse from(RefreshToken token) {
        return new SessionResponse(token.getId(), token.getCreatedAt(), token.getExpiresAt());
    }
}
