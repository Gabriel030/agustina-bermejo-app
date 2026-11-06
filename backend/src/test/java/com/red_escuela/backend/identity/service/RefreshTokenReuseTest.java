package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.identity.dto.AuthTokens;
import com.red_escuela.backend.identity.dto.ClientContext;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.exception.InvalidRefreshTokenException;
import com.red_escuela.backend.identity.repository.RefreshTokenRepository;
import com.red_escuela.backend.identity.repository.RefreshTokenState;
import com.red_escuela.backend.identity.util.TokenHashUtil;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshTokenReuseTest {

    private static final String RAW_TOKEN = "raw-refresh-token";
    private static final ClientContext CLIENT = ClientContext.of("browser-a", "10.0.0.1");

    private final RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
    private final AuthTokenIssuer issuer = mock(AuthTokenIssuer.class);
    private final SessionRevoker sessionRevoker = mock(SessionRevoker.class);
    private final RefreshTokenRotation service =
            new RefreshTokenRotation(repository, issuer, sessionRevoker, new RefreshReuseGracePolicy(30));
    private final String hash = TokenHashUtil.sha256Hex(RAW_TOKEN);
    private final Person person = activePerson();

    private Person activePerson() {
        Person person = new Person();
        person.setId(7L);
        person.setStatus(StatusType.ACTIVE);
        return person;
    }

    private void stored(RefreshTokenState state) {
        when(repository.findStateByTokenHash(hash)).thenReturn(Optional.of(state));
    }

    private StubRefreshTokenState liveToken() {
        return new StubRefreshTokenState(person, ApplicationTime.now().plusDays(1));
    }

    private StubRefreshTokenState rotatedSecondsAgo(long seconds, ClientContext rotatedBy) {
        return liveToken().rotated(ApplicationTime.now().minusSeconds(seconds), rotatedBy.userAgentHash());
    }

    @Test
    void reuseInsideGraceWindowIssuesChainedTokensAndKeepsOtherSessions() {
        StubRefreshTokenState token = rotatedSecondsAgo(5, CLIENT);
        stored(token);
        when(repository.markGraceUsedIfAvailable(hash)).thenReturn(1);
        AuthTokens tokens = new AuthTokens("access", "refresh", 900L);
        when(issuer.issueTokens(person, token.getId())).thenReturn(tokens);

        assertThat(service.rotate(RAW_TOKEN, CLIENT)).isSameAs(tokens);

        verify(sessionRevoker, never()).revokeAllSessions(any());
    }

    @Test
    void reuseAfterGraceWindowRevokesAllSessions() {
        stored(rotatedSecondsAgo(31, CLIENT));

        assertThatThrownBy(() -> service.rotate(RAW_TOKEN, CLIENT)).isInstanceOf(InvalidRefreshTokenException.class);

        verify(sessionRevoker).revokeAllSessions(person);
        verify(repository, never()).markGraceUsedIfAvailable(anyString());
    }

    @Test
    void reuseWithDifferentUserAgentRevokesAllSessions() {
        stored(rotatedSecondsAgo(5, ClientContext.of("browser-b", "10.0.0.1")));

        assertThatThrownBy(() -> service.rotate(RAW_TOKEN, CLIENT)).isInstanceOf(InvalidRefreshTokenException.class);

        verify(sessionRevoker).revokeAllSessions(person);
        verify(issuer, never()).issueTokens(any(), any());
    }

    @Test
    void secondReuseInsideWindowRevokesAllSessions() {
        stored(rotatedSecondsAgo(5, CLIENT));
        when(repository.markGraceUsedIfAvailable(hash)).thenReturn(0);

        assertThatThrownBy(() -> service.rotate(RAW_TOKEN, CLIENT)).isInstanceOf(InvalidRefreshTokenException.class);

        verify(sessionRevoker).revokeAllSessions(person);
        verify(issuer, never()).issueTokens(any(), any());
    }

    @Test
    void tokenRevokedWithoutRotationMarkRevokesAllSessionsEvenInsideWindow() {
        stored(liveToken().revoked());

        assertThatThrownBy(() -> service.rotate(RAW_TOKEN, CLIENT)).isInstanceOf(InvalidRefreshTokenException.class);

        verify(sessionRevoker).revokeAllSessions(person);
        verify(repository, never()).markGraceUsedIfAvailable(anyString());
    }

    @Test
    void unknownRefreshTokenDoesNotRevokeAnything() {
        when(repository.findStateByTokenHash(hash)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate(RAW_TOKEN, CLIENT)).isInstanceOf(InvalidRefreshTokenException.class);

        verify(sessionRevoker, never()).revokeAllSessions(any());
        verify(repository, never()).rotateIfActive(anyString(), any(), anyString());
    }

    @Test
    void expiredRevokedRefreshTokenDoesNotTriggerMassRevocation() {
        stored(new StubRefreshTokenState(person, ApplicationTime.now().minusMinutes(1))
                .rotated(ApplicationTime.now().minusSeconds(5), CLIENT.userAgentHash()));

        assertThatThrownBy(() -> service.rotate(RAW_TOKEN, CLIENT)).isInstanceOf(InvalidRefreshTokenException.class);

        verify(sessionRevoker, never()).revokeAllSessions(any());
    }

    @Test
    void activeRefreshTokenRotatesOnceChainedToItsParentWithoutRevokingOtherSessions() {
        StubRefreshTokenState token = liveToken();
        stored(token);
        when(repository.rotateIfActive(anyString(), any(LocalDateTime.class), anyString())).thenReturn(1);
        AuthTokens tokens = new AuthTokens("access", "refresh", 900L);
        when(issuer.issueTokens(person, token.getId())).thenReturn(tokens);

        assertThat(service.rotate(RAW_TOKEN, CLIENT)).isSameAs(tokens);

        verify(repository).rotateIfActive(anyString(), any(LocalDateTime.class), org.mockito.ArgumentMatchers.eq(CLIENT.userAgentHash()));
        verify(sessionRevoker, never()).revokeAllSessions(any());
    }

    @Test
    void loserOfARotationRaceRereadsTheTokenAndEntersTheGraceBranch() {
        StubRefreshTokenState beforeRace = liveToken();
        StubRefreshTokenState afterRace = rotatedSecondsAgo(0, CLIENT);
        when(repository.findStateByTokenHash(hash)).thenReturn(Optional.of(beforeRace), Optional.of(afterRace));
        when(repository.rotateIfActive(anyString(), any(LocalDateTime.class), anyString())).thenReturn(0);
        when(repository.markGraceUsedIfAvailable(hash)).thenReturn(1);
        AuthTokens tokens = new AuthTokens("access", "refresh", 900L);
        when(issuer.issueTokens(person, afterRace.getId())).thenReturn(tokens);

        assertThat(service.rotate(RAW_TOKEN, CLIENT)).isSameAs(tokens);

        verify(sessionRevoker, never()).revokeAllSessions(any());
    }

    @Test
    void loserOfARaceAgainstLogoutAnswers401WithoutRevokingOtherSessions() {
        StubRefreshTokenState beforeRace = liveToken();
        StubRefreshTokenState afterLogout = liveToken().revoked();
        when(repository.findStateByTokenHash(hash)).thenReturn(Optional.of(beforeRace), Optional.of(afterLogout));
        when(repository.rotateIfActive(anyString(), any(LocalDateTime.class), anyString())).thenReturn(0);

        assertThatThrownBy(() -> service.rotate(RAW_TOKEN, CLIENT)).isInstanceOf(InvalidRefreshTokenException.class);

        verify(sessionRevoker, never()).revokeAllSessions(any());
    }
}
