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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import com.red_escuela.backend.identity.events.PasswordResetLockoutNoticeEvent;
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
@RecordApplicationEvents
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
    @Value("${auth.password-reset-lockout.account-max-failures}")
    private int accountMaxFailures;
    @Value("${auth.password-reset-lockout.notice-interval-minutes}")
    private long noticeIntervalMinutes;
    @Autowired
    private ApplicationEvents applicationEvents;

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

    private void failCode(Person person, String ip) {
        assertThatThrownBy(() -> verifyResetCodeUseCase.verify(new VerifyResetCodeCommand(person.getEmail().value(), "000000", ip)))
                .isInstanceOf(InvalidPasswordResetTokenException.class);
    }

    private String verifyCode(Person person, String ip) {
        return verifyResetCodeUseCase.verify(new VerifyResetCodeCommand(person.getEmail().value(), CODE, ip));
    }

    private void failCodesUntilSourceLocked(Person person, String ip) {
        for (int i = 0; i < lockoutMaxAttempts; i++) {
            if (i % 4 == 0) {
                issueKnownCode(person);
            }
            failCode(person, ip);
        }
    }

    private long lockoutNotices() {
        return applicationEvents.stream(PasswordResetLockoutNoticeEvent.class).count();
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
        String exchangeToken = verifyCode(person, "10.0.0.1");

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
    void failuresFromOneSourceLockOnlyThatSourceAndTheOwnerCanStillRecoverFromAnother() {
        Person person = persistPerson("reset-lock-source");
        MutableClock.INSTANCE.setDateTime(START);
        failCodesUntilSourceLocked(person, "10.0.0.1");

        issueKnownCode(person);
        failCode(person, "10.0.0.1");
        assertThat(verifyCode(person, "10.0.0.2")).isNotBlank();
    }

    @Test
    void lockedSourceRecoversAfterTheLockWindowAndEmissionIsNeverBlocked() {
        Person person = persistPerson("reset-lock-expiry");
        MutableClock.INSTANCE.setDateTime(START);
        failCodesUntilSourceLocked(person, "10.0.0.1");
        long tokensBefore = passwordResetTokenRepository.count();

        issueKnownCode(person);
        assertThat(passwordResetTokenRepository.count()).isGreaterThan(tokensBefore);
        failCode(person, "10.0.0.1");

        MutableClock.INSTANCE.advance(Duration.ofMinutes(lockoutDurationMinutes + 1));
        issueKnownCode(person);
        assertThat(verifyCode(person, "10.0.0.1")).isNotBlank();
    }

    @Test
    void failuresBelowTheLimitDoNotLockAndASuccessClearsTheSourceCounter() {
        Person person = persistPerson("reset-lock-below");
        MutableClock.INSTANCE.setDateTime(START);
        issueKnownCode(person);
        failCode(person, "10.0.0.1");
        failCode(person, "10.0.0.1");

        assertThat(verifyCode(person, "10.0.0.1")).isNotBlank();
        assertThat(passwordResetTokenRepository.countBlockingFailures(person.getId(), TokenHashUtil.sha256Hex("10.0.0.1"), 1, START.minusDays(1))).isZero();
    }

    @Test
    void lockoutNoticeIsSentOnceAndAtMostOncePerHourPerAccount() {
        Person person = persistPerson("reset-lock-notice");
        MutableClock.INSTANCE.setDateTime(START);

        failCodesUntilSourceLocked(person, "10.0.0.1");
        assertThat(lockoutNotices()).isEqualTo(1);

        failCodesUntilSourceLocked(person, "10.0.0.2");
        assertThat(lockoutNotices()).isEqualTo(1);

        MutableClock.INSTANCE.advance(Duration.ofMinutes(noticeIntervalMinutes + 1));
        failCodesUntilSourceLocked(person, "10.0.0.3");
        assertThat(lockoutNotices()).isEqualTo(2);
    }

    @Test
    void hardAccountCapInvalidatesTheCurrentCodeButTheOwnerCanRequestAndUseANewOne() {
        Person person = persistPerson("reset-hard-cap");
        MutableClock.INSTANCE.setDateTime(START);
        for (int i = 0; i < accountMaxFailures; i++) {
            if (i % 4 == 0) {
                issueKnownCode(person);
            }
            failCode(person, "10.0." + (i / 9) + ".1");
        }

        assertThatThrownBy(() -> verifyCode(person, "10.9.9.9")).isInstanceOf(InvalidPasswordResetTokenException.class);

        issueKnownCode(person);
        assertThat(verifyCode(person, "10.9.9.9")).isNotBlank();
    }

    @Test
    void unknownAccountAndLockedSourceAnswerWithTheSameError() {
        Person person = persistPerson("reset-same-error");
        MutableClock.INSTANCE.setDateTime(START);
        failCodesUntilSourceLocked(person, "10.0.0.1");
        issueKnownCode(person);

        Throwable locked = org.assertj.core.api.Assertions.catchThrowable(() -> verifyCode(person, "10.0.0.1"));
        Throwable unknown = org.assertj.core.api.Assertions.catchThrowable(
                () -> verifyResetCodeUseCase.verify(new VerifyResetCodeCommand("nobody@example.com", CODE, "10.0.0.1")));

        assertThat(locked).isInstanceOf(InvalidPasswordResetTokenException.class);
        assertThat(unknown).isInstanceOf(locked.getClass()).hasSameClassAs(locked);
        assertThat(unknown.getMessage()).isEqualTo(locked.getMessage());
    }
}
