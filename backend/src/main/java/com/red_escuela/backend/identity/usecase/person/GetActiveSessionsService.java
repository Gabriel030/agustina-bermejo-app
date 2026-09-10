package com.red_escuela.backend.identity.usecase.person;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.RefreshToken;
import com.red_escuela.backend.identity.repository.RefreshTokenRepository;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import com.red_escuela.backend.shared.UseCase;

import java.time.LocalDateTime;
import java.util.List;

@UseCase
public class GetActiveSessionsService implements GetActiveSessionsUseCase {

    private final RefreshTokenRepository refreshTokenRepository;
    private final CurrentPersonService currentPersonService;

    public GetActiveSessionsService(RefreshTokenRepository refreshTokenRepository,
                                    CurrentPersonService currentPersonService) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.currentPersonService = currentPersonService;
    }

    @Override
    public List<RefreshToken> getActiveSessions() {
        Person person = currentPersonService.resolveCurrentPerson();
        return refreshTokenRepository.findByPersonAndRevokedFalseAndExpiresAtAfter(person, LocalDateTime.now());
    }
}
