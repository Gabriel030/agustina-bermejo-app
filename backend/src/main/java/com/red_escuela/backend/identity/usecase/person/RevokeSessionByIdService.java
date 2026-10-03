package com.red_escuela.backend.identity.usecase.person;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.RefreshToken;
import com.red_escuela.backend.identity.exception.InvalidRefreshTokenException;
import com.red_escuela.backend.identity.repository.RefreshTokenRepository;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class RevokeSessionByIdService implements RevokeSessionByIdUseCase {

    private final RefreshTokenRepository refreshTokenRepository;

    public RevokeSessionByIdService(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Override
    @Transactional
    public void revokeById(Long sessionId, Person person) {
        RefreshToken token = refreshTokenRepository.findById(sessionId)
                .filter(t -> t.getPerson().getId().equals(person.getId()))
                .orElseThrow(InvalidRefreshTokenException::new);
        token.setRevoked(true);
        refreshTokenRepository.save(token);
    }
}
