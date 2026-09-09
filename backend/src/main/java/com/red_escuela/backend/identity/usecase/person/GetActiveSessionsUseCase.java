package com.red_escuela.backend.identity.usecase.person;

import com.red_escuela.backend.identity.entity.RefreshToken;

import java.util.List;

public interface GetActiveSessionsUseCase {
    List<RefreshToken> getActiveSessions();
}
