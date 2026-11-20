package com.red_escuela.backend.identity.repository;

import com.red_escuela.backend.shared.domain.StatusType;

public record AccountAccessState(StatusType status, int tokenVersion) {

    public boolean admits(int claimedTokenVersion) {
        return status == StatusType.ACTIVE && tokenVersion == claimedTokenVersion;
    }
}
