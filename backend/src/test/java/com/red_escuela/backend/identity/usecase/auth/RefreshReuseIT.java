package com.red_escuela.backend.identity.usecase.auth;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.dto.AuthTokens;
import com.red_escuela.backend.identity.dto.ClientContext;
import com.red_escuela.backend.identity.dto.ClientContext;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.exception.InvalidRefreshTokenException;
import com.red_escuela.backend.identity.repository.AddressRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.RefreshTokenRepository;
import com.red_escuela.backend.identity.service.AuthTokenIssuer;
import com.red_escuela.backend.identity.service.SessionRevoker;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.time.ApplicationTime;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class RefreshReuseIT extends AbstractIntegrationTest {

    private static final long GRACE_SECONDS = 30;
    private static final ClientContext CLIENT = ClientContext.of("browser-a", "10.0.0.1");

    @Autowired
    private RefreshTokenUseCase refreshTokenUseCase;
    @Autowired
    private LogoutUseCase logoutUseCase;
    @Autowired
    private AuthTokenIssuer authTokenIssuer;
    @Autowired
    private SessionRevoker sessionRevoker;
    @Autowired
    private PersonRepository personRepository;
    @Autowired
    private AddressRepository addressRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private void startFixedClock() {
        MutableClock.INSTANCE.setDateTime(java.time.LocalDateTime.of(2026, 11, 7, 10, 0));
    }

    private Person persistPerson(String username) {
        Person person = new Person();
        person.setUsername(username);
        person.setEmail(username + "@example.com");
        person.setAddress(addressRepository.save(new Address()));
        person.setStatus(StatusType.ACTIVE);
        return personRepository.save(person);
    }

    private long activeSessions(Person person) {
        return refreshTokenRepository.findByPersonAndRevokedFalseAndExpiresAtAfter(
                person, ApplicationTime.now().minusDays(1)).size();
    }

    @Test
    void reusingRotatedRefreshTokenAfterTheGraceWindowRevokesTheSuccessorAndEveryOtherSession() {
        startFixedClock();
        Person person = persistPerson("reuse-rotated-" + System.nanoTime());
        String tokenA = authTokenIssuer.issueTokens(person).refreshToken();
        String otherDevice = authTokenIssuer.issueTokens(person).refreshToken();
        AuthTokens rotated = refreshTokenUseCase.refresh(tokenA, CLIENT);
        MutableClock.INSTANCE.advance(Duration.ofSeconds(GRACE_SECONDS + 1));

        assertThatThrownBy(() -> refreshTokenUseCase.refresh(tokenA, CLIENT)).isInstanceOf(InvalidRefreshTokenException.class);

        assertThat(activeSessions(person)).isZero();
        assertThatThrownBy(() -> refreshTokenUseCase.refresh(rotated.refreshToken(), CLIENT))
                .isInstanceOf(InvalidRefreshTokenException.class);
        assertThatThrownBy(() -> refreshTokenUseCase.refresh(otherDevice, CLIENT))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void reusingRefreshTokenClosedByLogoutRevokesAllSessionsOfThePerson() {
        Person person = persistPerson("reuse-logout-" + System.nanoTime());
        String closedByLogout = authTokenIssuer.issueTokens(person).refreshToken();
        authTokenIssuer.issueTokens(person);
        logoutUseCase.logout(closedByLogout);
        assertThat(activeSessions(person)).isEqualTo(1);

        assertThatThrownBy(() -> refreshTokenUseCase.refresh(closedByLogout, CLIENT))
                .isInstanceOf(InvalidRefreshTokenException.class);

        assertThat(activeSessions(person)).isZero();
    }

    @Test
    void reuseInsideGraceWindowReturnsNewTokenPairAndKeepsOtherSessions() {
        startFixedClock();
        Person person = persistPerson("grace-inside-" + System.nanoTime());
        String tokenA = authTokenIssuer.issueTokens(person).refreshToken();
        String otherDevice = authTokenIssuer.issueTokens(person).refreshToken();
        AuthTokens rotated = refreshTokenUseCase.refresh(tokenA, CLIENT);
        MutableClock.INSTANCE.advance(Duration.ofSeconds(GRACE_SECONDS));

        AuthTokens retried = refreshTokenUseCase.refresh(tokenA, CLIENT);

        assertThat(retried.refreshToken()).isNotIn(tokenA, rotated.refreshToken());
        assertThat(activeSessions(person)).isEqualTo(3);
        assertThat(refreshTokenUseCase.refresh(otherDevice, CLIENT).refreshToken()).isNotBlank();
    }

    @Test
    void secondReuseInsideWindowRevokesAllSessions() {
        startFixedClock();
        Person person = persistPerson("grace-second-" + System.nanoTime());
        String tokenA = authTokenIssuer.issueTokens(person).refreshToken();
        refreshTokenUseCase.refresh(tokenA, CLIENT);
        refreshTokenUseCase.refresh(tokenA, CLIENT);

        assertThatThrownBy(() -> refreshTokenUseCase.refresh(tokenA, CLIENT)).isInstanceOf(InvalidRefreshTokenException.class);

        assertThat(activeSessions(person)).isZero();
    }

    @Test
    void reuseWithDifferentUserAgentRevokesAllSessions() {
        startFixedClock();
        Person person = persistPerson("grace-agent-" + System.nanoTime());
        String tokenA = authTokenIssuer.issueTokens(person).refreshToken();
        refreshTokenUseCase.refresh(tokenA, CLIENT);

        assertThatThrownBy(() -> refreshTokenUseCase.refresh(tokenA, ClientContext.of("browser-b", "10.0.0.1")))
                .isInstanceOf(InvalidRefreshTokenException.class);

        assertThat(activeSessions(person)).isZero();
    }

    @Test
    void logoutTokenReusedInsideWindowRevokesAllSessions() {
        startFixedClock();
        Person person = persistPerson("grace-logout-" + System.nanoTime());
        String closedByLogout = authTokenIssuer.issueTokens(person).refreshToken();
        authTokenIssuer.issueTokens(person);
        logoutUseCase.logout(closedByLogout);

        assertThatThrownBy(() -> refreshTokenUseCase.refresh(closedByLogout, CLIENT))
                .isInstanceOf(InvalidRefreshTokenException.class);

        assertThat(activeSessions(person)).isZero();
    }

    @Test
    void tokenRevokedByPasswordChangeNeverGetsGrace() {
        startFixedClock();
        Person person = persistPerson("grace-password-" + System.nanoTime());
        String token = authTokenIssuer.issueTokens(person).refreshToken();
        sessionRevoker.revokeAllSessions(person);
        String freshSession = authTokenIssuer.issueTokens(person).refreshToken();

        assertThatThrownBy(() -> refreshTokenUseCase.refresh(token, CLIENT)).isInstanceOf(InvalidRefreshTokenException.class);

        assertThat(activeSessions(person)).isZero();
        assertThatThrownBy(() -> refreshTokenUseCase.refresh(freshSession, CLIENT))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void unknownTokenNeverGetsGraceNorRevokesAnyoneElse() {
        startFixedClock();
        Person person = persistPerson("grace-unknown-" + System.nanoTime());
        authTokenIssuer.issueTokens(person);

        assertThatThrownBy(() -> refreshTokenUseCase.refresh("not-a-real-token", CLIENT))
                .isInstanceOf(InvalidRefreshTokenException.class);

        assertThat(activeSessions(person)).isEqualTo(1);
    }

    @Test
    void graceIssuedTokenCanRotateNormallyAndGetsItsOwnGrace() {
        startFixedClock();
        Person person = persistPerson("grace-chain-" + System.nanoTime());
        String tokenA = authTokenIssuer.issueTokens(person).refreshToken();
        refreshTokenUseCase.refresh(tokenA, CLIENT);
        String fromGrace = refreshTokenUseCase.refresh(tokenA, CLIENT).refreshToken();

        AuthTokens rotated = refreshTokenUseCase.refresh(fromGrace, CLIENT);
        AuthTokens retried = refreshTokenUseCase.refresh(fromGrace, CLIENT);

        assertThat(rotated.refreshToken()).isNotEqualTo(retried.refreshToken());
    }

    @Test
    void revokeAllInsideWindowAfterRotationKillsTheGraceOfTheRotatedToken() {
        startFixedClock();
        Person person = persistPerson("grace-revokeall-" + System.nanoTime());
        String tokenA = authTokenIssuer.issueTokens(person).refreshToken();
        refreshTokenUseCase.refresh(tokenA, CLIENT);
        MutableClock.INSTANCE.advance(Duration.ofSeconds(10));
        sessionRevoker.revokeAllSessions(person);
        MutableClock.INSTANCE.advance(Duration.ofSeconds(10));

        assertThatThrownBy(() -> refreshTokenUseCase.refresh(tokenA, CLIENT)).isInstanceOf(InvalidRefreshTokenException.class);

        assertThat(activeSessions(person)).isZero();
    }

    @Test
    void logoutOfTheChildInsideWindowKillsTheGraceOfTheParent() {
        startFixedClock();
        Person person = persistPerson("grace-childlogout-" + System.nanoTime());
        String tokenA = authTokenIssuer.issueTokens(person).refreshToken();
        String child = refreshTokenUseCase.refresh(tokenA, CLIENT).refreshToken();
        logoutUseCase.logout(child);

        assertThatThrownBy(() -> refreshTokenUseCase.refresh(tokenA, CLIENT)).isInstanceOf(InvalidRefreshTokenException.class);

        assertThat(activeSessions(person)).isZero();
    }

    @Test
    void revokeSessionOfTheChildInsideWindowKillsTheGraceOfTheParent() {
        startFixedClock();
        Person person = persistPerson("grace-childsession-" + System.nanoTime());
        String tokenA = authTokenIssuer.issueTokens(person).refreshToken();
        String child = refreshTokenUseCase.refresh(tokenA, CLIENT).refreshToken();
        Long childId = refreshTokenRepository.findByTokenHash(com.red_escuela.backend.identity.util.TokenHashUtil.sha256Hex(child))
                .orElseThrow().getId();
        sessionRevoker.revokeSession(childId, person);

        assertThatThrownBy(() -> refreshTokenUseCase.refresh(tokenA, CLIENT)).isInstanceOf(InvalidRefreshTokenException.class);

        assertThat(activeSessions(person)).isZero();
    }

    @Test
    void missingUserAgentNeverEnablesGrace() {
        startFixedClock();
        Person person = persistPerson("grace-noagent-" + System.nanoTime());
        ClientContext noAgent = ClientContext.of(null, "10.0.0.1");
        String tokenA = authTokenIssuer.issueTokens(person).refreshToken();
        refreshTokenUseCase.refresh(tokenA, noAgent);

        assertThatThrownBy(() -> refreshTokenUseCase.refresh(tokenA, noAgent)).isInstanceOf(InvalidRefreshTokenException.class);

        assertThat(activeSessions(person)).isZero();
    }
}
