package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.identity.dto.AuthTokens;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.RefreshToken;
import com.red_escuela.backend.identity.exception.InvalidRefreshTokenException;
import com.red_escuela.backend.identity.repository.RefreshTokenRepository;
import com.red_escuela.backend.identity.util.TokenHashUtil;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshTokenReuseTest {

    private static final String RAW_TOKEN = "raw-refresh-token";

    private final RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
    private final AuthTokenIssuer issuer = mock(AuthTokenIssuer.class);
    private final SessionRevoker sessionRevoker = mock(SessionRevoker.class);
    private final RefreshTokenRotation service = new RefreshTokenRotation(repository, issuer, sessionRevoker);
    private final String hash = TokenHashUtil.sha256Hex(RAW_TOKEN);

    private RefreshToken storedToken(Person person, boolean revoked, LocalDateTime expiresAt) {
        RefreshToken token = new RefreshToken();
        token.setPerson(person);
        token.setTokenHash(hash);
        token.setRevoked(revoked);
        token.setExpiresAt(expiresAt);
        when(repository.findByTokenHash(hash)).thenReturn(Optional.of(token));
        return token;
    }

    private Person activePerson() {
        Person person = new Person();
        person.setId(7L);
        person.setStatus(StatusType.ACTIVE);
        return person;
    }

    @Test
    void reusedRevokedRefreshTokenRevokesAllSessionsOfThePerson() {
        Person person = activePerson();
        storedToken(person, true, ApplicationTime.now().plusDays(1));

        assertThatThrownBy(() -> service.rotate(RAW_TOKEN)).isInstanceOf(InvalidRefreshTokenException.class);

        verify(sessionRevoker).revokeAllSessions(person);
    }

    @Test
    void unknownRefreshTokenDoesNotRevokeAnything() {
        when(repository.findByTokenHash(hash)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate(RAW_TOKEN)).isInstanceOf(InvalidRefreshTokenException.class);

        verify(sessionRevoker, never()).revokeAllSessions(any());
    }

    @Test
    void expiredRevokedRefreshTokenDoesNotTriggerMassRevocation() {
        storedToken(activePerson(), true, ApplicationTime.now().minusMinutes(1));

        assertThatThrownBy(() -> service.rotate(RAW_TOKEN)).isInstanceOf(InvalidRefreshTokenException.class);

        verify(sessionRevoker, never()).revokeAllSessions(any());
    }

    @Test
    void activeRefreshTokenRotatesOnceWithoutRevokingOtherSessions() {
        Person person = activePerson();
        storedToken(person, false, ApplicationTime.now().plusDays(1));
        when(repository.revokeIfActive(hash)).thenReturn(1);
        AuthTokens tokens = new AuthTokens("access", "refresh", 900L);
        when(issuer.issueTokens(person)).thenReturn(tokens);

        assertThat(service.rotate(RAW_TOKEN)).isSameAs(tokens);

        verify(repository).revokeIfActive(hash);
        verify(sessionRevoker, never()).revokeAllSessions(any());
    }
}
