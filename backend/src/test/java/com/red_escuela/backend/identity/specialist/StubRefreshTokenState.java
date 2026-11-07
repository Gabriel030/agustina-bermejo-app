package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.RefreshTokenState;

import java.time.LocalDateTime;

final class StubRefreshTokenState implements RefreshTokenState {

    private Long id = 11L;
    private Person person;
    private Boolean revoked = false;
    private LocalDateTime expiresAt;
    private LocalDateTime rotatedAt;
    private String rotatedUserAgentHash;
    private Boolean graceUsed = false;

    StubRefreshTokenState(Person person, LocalDateTime expiresAt) {
        this.person = person;
        this.expiresAt = expiresAt;
    }

    StubRefreshTokenState revoked() {
        this.revoked = true;
        return this;
    }

    StubRefreshTokenState rotated(LocalDateTime rotatedAt, String userAgentHash) {
        this.revoked = true;
        this.rotatedAt = rotatedAt;
        this.rotatedUserAgentHash = userAgentHash;
        return this;
    }

    StubRefreshTokenState graceAlreadyUsed() {
        this.graceUsed = true;
        return this;
    }

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public Person getPerson() {
        return person;
    }

    @Override
    public Boolean getRevoked() {
        return revoked;
    }

    @Override
    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    @Override
    public LocalDateTime getRotatedAt() {
        return rotatedAt;
    }

    @Override
    public String getRotatedUserAgentHash() {
        return rotatedUserAgentHash;
    }

    @Override
    public Boolean getGraceUsed() {
        return graceUsed;
    }
}
