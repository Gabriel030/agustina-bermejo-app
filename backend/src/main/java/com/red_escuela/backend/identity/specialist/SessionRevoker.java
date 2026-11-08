package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.RefreshToken;
import com.red_escuela.backend.identity.exception.SessionNotFoundException;
import com.red_escuela.backend.identity.repository.RefreshTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SessionRevoker {
    private final RefreshTokenRepository refreshTokenRepository;

    public SessionRevoker(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional
    public void revokeAllSessions(Person person) {
        List<RefreshToken> tokens = refreshTokenRepository.findByPerson(person);
        tokens.forEach(token -> {
            token.setRevoked(true);
            token.setGraceUsed(true);
        });
        refreshTokenRepository.saveAll(tokens);
    }

    @Transactional
    public void revokeSession(Long sessionId, Person person) {
        RefreshToken token = refreshTokenRepository.findById(sessionId)
                .filter(t -> t.getPerson().getId().equals(person.getId()))
                .orElseThrow(() -> new SessionNotFoundException(sessionId));
        token.setRevoked(true);
        token.setGraceUsed(true);
        refreshTokenRepository.save(token);
        if (token.getParentId() != null) {
            refreshTokenRepository.disableGraceById(token.getParentId());
        }
    }
}
