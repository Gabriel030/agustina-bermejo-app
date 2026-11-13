package com.red_escuela.backend.identity.usecase.auth;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.api.AccessTokenVerifier;
import com.red_escuela.backend.identity.dto.AuthTokens;
import com.red_escuela.backend.identity.entity.AuthProvider;
import com.red_escuela.backend.identity.entity.PasswordResetToken;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.exception.InvalidPasswordResetTokenException;
import com.red_escuela.backend.identity.repository.PasswordResetTokenRepository;
import com.red_escuela.backend.identity.specialist.SessionRevoker;
import com.red_escuela.backend.identity.util.TokenHashUtil;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.time.MutableClock;
import com.red_escuela.backend.testsupport.SharedMockBeans;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@SharedMockBeans
class AccessTokenRevalidationAndResetLockoutTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "correcta123";
    private static final String NEW_PASSWORD = "Nueva12345";
    private static final String CODE = "123456";
    private static final LocalDateTime START = LocalDateTime.of(2026, 3, 10, 9, 0);

    @Autowired
    private LoginUseCase loginUseCase;
    @Autowired
    private AccessTokenVerifier accessTokenVerifier;
    @Autowired
    private SessionRevoker sessionRevoker;
    @Autowired
    private RequestPasswordResetUseCase requestPasswordResetUseCase;
    @Autowired
    private VerifyResetCodeUseCase verifyResetCodeUseCase;
    @Autowired
    private ResetPasswordUseCase resetPasswordUseCase;
    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private EntityManager entityManager;

    @Value("${auth.access-state-cache-seconds}")
    private long cacheSeconds;
    @Value("${auth.password-reset-lockout.max-attempts}")
    private int lockoutMaxAttempts;
    @Value("${auth.password-reset-lockout.duration-minutes}")
    private long lockoutDurationMinutes;
    @Value("${auth.password-reset-code-max-attempts}")
    private int codeMaxAttempts;

    private Person persistPerson(String username) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setUsername(username);
        person.setEmail(username + "@example.com");
        person.setPassword(passwordEncoder.encode(PASSWORD));
        person.setAuthProvider(AuthProvider.LOCAL);
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        entityManager.persist(person);
        entityManager.flush();
        return person;
    }

    private String login(String username) {
        AuthTokens tokens = loginUseCase.login(new LoginCommand(username, PASSWORD, "127.0.0.1"));
        return tokens.accessToken();
    }

    private void issueKnownCode(Person person) {
        requestPasswordResetUseCase.requestReset(person.getEmail().value());
        entityManager.flush();
        PasswordResetToken token = latestToken(person);
        token.setTokenHash(TokenHashUtil.sha256Hex(CODE));
        entityManager.flush();
    }

    private PasswordResetToken latestToken(Person person) {
        List<PasswordResetToken> tokens = passwordResetTokenRepository.findAll().stream()
                .filter(candidate -> candidate.getPerson().getId().equals(person.getId()))
                .toList();
        return tokens.get(tokens.size() - 1);
    }

    private void failCode(Person person, String wrongCode) {
        assertThatThrownBy(() -> verifyResetCodeUseCase.verify(new VerifyResetCodeCommand(person.getEmail().value(), wrongCode)))
                .isInstanceOf(InvalidPasswordResetTokenException.class);
    }

    private void failCodesUntilAccountLocked(Person person) {
        for (int i = 0; i < lockoutMaxAttempts; i++) {
            if (i % codeMaxAttempts == 0) {
                issueKnownCode(person);
            }
            failCode(person, "000000");
        }
    }

    @Test
    void activeAccountTokenIsAccepted() {
        persistPerson("revalidation-ok");
        MutableClock.INSTANCE.setDateTime(START);

        assertThat(accessTokenVerifier.verify(login("revalidation-ok"))).isPresent();
    }

    @Test
    void tokenOfAWithdrawnAccountStopsWorkingOnceTheCacheWindowEnds() {
        Person person = persistPerson("revalidation-baja");
        MutableClock.INSTANCE.setDateTime(START);
        String token = login("revalidation-baja");
        assertThat(accessTokenVerifier.verify(token)).isPresent();

        person.setStatus(StatusType.INACTIVE);
        entityManager.flush();

        MutableClock.INSTANCE.advance(Duration.ofSeconds(cacheSeconds - 1));
        assertThat(accessTokenVerifier.verify(token)).isPresent();

        MutableClock.INSTANCE.advance(Duration.ofSeconds(2));
        assertThat(accessTokenVerifier.verify(token)).isEmpty();
    }

    @Test
    void revokingSessionsRejectsPreviousTokensImmediatelyAndKeepsNewOnesValid() {
        Person person = persistPerson("revalidation-revoke");
        MutableClock.INSTANCE.setDateTime(START);
        String oldToken = login("revalidation-revoke");
        assertThat(accessTokenVerifier.verify(oldToken)).isPresent();

        sessionRevoker.revokeAllSessions(person);

        assertThat(accessTokenVerifier.verify(oldToken)).isEmpty();
        assertThat(accessTokenVerifier.verify(login("revalidation-revoke"))).isPresent();
    }

    @Test
    void passwordResetInvalidatesTheAccessTokensIssuedBefore() {
        Person person = persistPerson("revalidation-reset");
        MutableClock.INSTANCE.setDateTime(START);
        String oldToken = login("revalidation-reset");
        assertThat(accessTokenVerifier.verify(oldToken)).isPresent();
        issueKnownCode(person);
        String exchangeToken = verifyResetCodeUseCase.verify(new VerifyResetCodeCommand(person.getEmail().value(), CODE));

        resetPasswordUseCase.reset(new ResetPasswordCommand(exchangeToken, NEW_PASSWORD));

        assertThat(accessTokenVerifier.verify(oldToken)).isEmpty();
    }

    @Test
    void tokenOfAnUnknownPersonIsRejected() {
        Person person = persistPerson("revalidation-unknown");
        MutableClock.INSTANCE.setDateTime(START);
        String token = login("revalidation-unknown");
        entityManager.remove(person);
        entityManager.flush();
        MutableClock.INSTANCE.advance(Duration.ofSeconds(cacheSeconds + 1));

        assertThat(accessTokenVerifier.verify(token)).isEmpty();
    }

    @Test
    void wrongCodesAcrossSeveralIssuedCodesLockTheResetOfTheAccount() {
        Person person = persistPerson("reset-lock");
        MutableClock.INSTANCE.setDateTime(START);

        failCodesUntilAccountLocked(person);

        assertThat(person.getResetLockedUntil()).isEqualTo(START.plusMinutes(lockoutDurationMinutes));
        issueKnownCode(person);
        failCode(person, CODE);
    }

    @Test
    void lockedAccountDoesNotReceiveNewCodesAndRecoversAfterTheLockDuration() {
        Person person = persistPerson("reset-lock-expiry");
        MutableClock.INSTANCE.setDateTime(START);
        failCodesUntilAccountLocked(person);
        long tokensBefore = passwordResetTokenRepository.count();

        requestPasswordResetUseCase.requestReset(person.getEmail().value());
        entityManager.flush();
        assertThat(passwordResetTokenRepository.count()).isEqualTo(tokensBefore);

        MutableClock.INSTANCE.advance(Duration.ofMinutes(lockoutDurationMinutes + 1));
        issueKnownCode(person);
        assertThat(verifyResetCodeUseCase.verify(new VerifyResetCodeCommand(person.getEmail().value(), CODE))).isNotBlank();
    }

    @Test
    void failuresBelowTheLimitDoNotLockAndASuccessClearsTheCounter() {
        Person person = persistPerson("reset-lock-below");
        MutableClock.INSTANCE.setDateTime(START);
        issueKnownCode(person);
        failCode(person, "000000");
        failCode(person, "000000");
        assertThat(person.getResetFailedAttempts()).isEqualTo(2);
        assertThat(person.getResetLockedUntil()).isNull();

        assertThat(verifyResetCodeUseCase.verify(new VerifyResetCodeCommand(person.getEmail().value(), CODE))).isNotBlank();
        assertThat(person.getResetFailedAttempts()).isZero();
    }
}
