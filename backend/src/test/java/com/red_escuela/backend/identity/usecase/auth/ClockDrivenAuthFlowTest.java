package com.red_escuela.backend.identity.usecase.auth;

import com.red_escuela.backend.testsupport.SharedMockBeans;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.AuthProvider;
import com.red_escuela.backend.identity.entity.EmailVerificationToken;
import com.red_escuela.backend.identity.entity.PasswordResetToken;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.exception.InvalidCredentialsException;
import com.red_escuela.backend.shared.exception.InvalidEmailVerificationTokenException;
import com.red_escuela.backend.identity.exception.InvalidPasswordResetTokenException;
import com.red_escuela.backend.identity.repository.EmailVerificationTokenRepository;
import com.red_escuela.backend.identity.repository.PasswordResetTokenRepository;
import com.red_escuela.backend.identity.usecase.emailverification.VerifyEmailUseCase;
import com.red_escuela.backend.identity.util.TokenHashUtil;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.mail.EmailService;
import com.red_escuela.backend.shared.time.MutableClock;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@SharedMockBeans
class ClockDrivenAuthFlowTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "correcta123";

    @Autowired
    private LoginUseCase loginUseCase;

    @Autowired
    private RequestPasswordResetUseCase requestPasswordResetUseCase;

    @Autowired
    private VerifyResetCodeUseCase verifyResetCodeUseCase;

    @Autowired
    private VerifyEmailUseCase verifyEmailUseCase;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EmailService emailService;

    @Value("${auth.lockout.max-attempts}")
    private int lockoutMaxAttempts;

    @Value("${auth.lockout.duration-minutes}")
    private long lockoutDurationMinutes;

    @Value("${auth.password-reset-code-expiration-minutes}")
    private long resetCodeExpirationMinutes;

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

    private void failLoginUntilLocked(String username) {
        for (int i = 0; i < lockoutMaxAttempts; i++) {
            assertThatThrownBy(() -> loginUseCase.login(new LoginCommand(username, "mal", "127.0.0.1")))
                    .isInstanceOf(InvalidCredentialsException.class);
        }
    }

    @Test
    void accountLockoutIsLiftedOnceTheLockDurationHasPassed() {
        persistPerson("bloqueo-reloj");
        LocalDateTime start = LocalDateTime.of(2026, 3, 10, 9, 0);
        MutableClock.INSTANCE.setDateTime(start);
        failLoginUntilLocked("bloqueo-reloj");

        MutableClock.INSTANCE.advance(Duration.ofMinutes(lockoutDurationMinutes - 1));
        assertThatThrownBy(() -> loginUseCase.login(new LoginCommand("bloqueo-reloj", PASSWORD, "127.0.0.1")))
                .isInstanceOf(InvalidCredentialsException.class);

        MutableClock.INSTANCE.advance(Duration.ofMinutes(2));
        assertThat(loginUseCase.login(new LoginCommand("bloqueo-reloj", PASSWORD, "127.0.0.1")).accessToken()).isNotBlank();
    }

    @Test
    void passwordResetCodeIsAcceptedWhileTheClockIsBeforeItsExpiration() {
        Person person = persistPerson("reset-reloj");
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 3, 10, 9, 0));
        requestPasswordResetUseCase.requestReset(person.getEmail().value());
        entityManager.flush();
        PasswordResetToken token = passwordResetTokenRepository.findAll().stream()
                .filter(candidate -> candidate.getPerson().getId().equals(person.getId()))
                .findFirst().orElseThrow();
        token.setTokenHash(TokenHashUtil.sha256Hex("123456"));
        entityManager.flush();

        MutableClock.INSTANCE.advance(Duration.ofMinutes(resetCodeExpirationMinutes - 1));
        assertThat(verifyResetCodeUseCase.verify(new VerifyResetCodeCommand(person.getEmail().value(), "123456"))).isNotBlank();
    }

    @Test
    void passwordResetCodeExpiresWhenTheClockPassesItsExpiration() {
        Person person = persistPerson("reset-vencido-reloj");
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 3, 10, 9, 0));
        requestPasswordResetUseCase.requestReset(person.getEmail().value());
        entityManager.flush();
        PasswordResetToken token = passwordResetTokenRepository.findAll().stream()
                .filter(candidate -> candidate.getPerson().getId().equals(person.getId()))
                .findFirst().orElseThrow();
        token.setTokenHash(TokenHashUtil.sha256Hex("123456"));
        entityManager.flush();

        MutableClock.INSTANCE.advance(Duration.ofMinutes(resetCodeExpirationMinutes + 1));

        assertThatThrownBy(() -> verifyResetCodeUseCase.verify(new VerifyResetCodeCommand(person.getEmail().value(), "123456")))
                .isInstanceOf(InvalidPasswordResetTokenException.class);
    }

    @Test
    void emailVerificationTokenIsAcceptedBeforeAndRejectedAfterItsExpiration() {
        Person person = persistPerson("verificacion-reloj");
        LocalDateTime expiresAt = LocalDateTime.of(2026, 3, 12, 9, 0);
        EmailVerificationToken token = new EmailVerificationToken();
        token.setPerson(person);
        token.setTokenHash(TokenHashUtil.sha256Hex("clear-token"));
        token.setExpiresAt(expiresAt);
        emailVerificationTokenRepository.save(token);
        entityManager.flush();

        MutableClock.INSTANCE.setDateTime(expiresAt.plusSeconds(1));
        assertThatThrownBy(() -> verifyEmailUseCase.verify("clear-token"))
                .isInstanceOf(InvalidEmailVerificationTokenException.class);

        MutableClock.INSTANCE.setDateTime(expiresAt.minusSeconds(1));
        assertThatCode(() -> verifyEmailUseCase.verify("clear-token")).doesNotThrowAnyException();
    }
}
