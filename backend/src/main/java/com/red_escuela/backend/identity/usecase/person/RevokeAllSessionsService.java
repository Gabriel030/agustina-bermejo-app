package com.red_escuela.backend.identity.usecase.person;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.RefreshToken;
import com.red_escuela.backend.identity.repository.RefreshTokenRepository;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@UseCase
public class RevokeAllSessionsService implements RevokeAllSessionsUseCase {

    private final RefreshTokenRepository refreshTokenRepository;

    public RevokeAllSessionsService(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Override
    @Transactional
    public void revokeAll(Person person) {
        List<RefreshToken> tokens = refreshTokenRepository.findByPerson(person);
        tokens.forEach(t -> t.setRevoked(true));
        refreshTokenRepository.saveAll(tokens);
    }
}
