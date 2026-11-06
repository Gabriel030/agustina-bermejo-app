package com.red_escuela.backend.identity.repository;

import com.red_escuela.backend.identity.entity.Person;

import java.time.LocalDateTime;

public interface RefreshTokenState {
    Long getId();

    Person getPerson();

    Boolean getRevoked();

    LocalDateTime getExpiresAt();

    LocalDateTime getRotatedAt();

    String getRotatedUserAgentHash();

    Boolean getGraceUsed();
}
