package com.red_escuela.backend.service;
import com.red_escuela.backend.identity.usecase.auth.AuthTokens;

import com.red_escuela.backend.identity.usecase.auth.LoginCommand;
import com.red_escuela.backend.identity.usecase.auth.LoginUseCase;
import com.red_escuela.backend.identity.usecase.auth.RequestPasswordResetUseCase;
import com.red_escuela.backend.identity.usecase.auth.ResetPasswordCommand;
import com.red_escuela.backend.identity.usecase.auth.ResetPasswordUseCase;
import com.red_escuela.backend.identity.usecase.auth.VerifyResetCodeCommand;
import com.red_escuela.backend.identity.usecase.auth.VerifyResetCodeUseCase;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.identity.entity.AuthProvider;
import com.red_escuela.backend.identity.entity.PasswordResetToken;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.identity.exception.InvalidCredentialsException;
import com.red_escuela.backend.identity.exception.InvalidPasswordPolicyException;
import com.red_escuela.backend.identity.exception.InvalidRefreshTokenException;
import com.red_escuela.backend.identity.usecase.auth.RefreshTokenUseCase;
import com.red_escuela.backend.identity.exception.InvalidPasswordResetTokenException;
import com.red_escuela.backend.identity.repository.PasswordResetTokenRepository;
import com.red_escuela.backend.util.TokenHashUtil;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PasswordResetTest extends AbstractIntegrationTest {
    @Autowired
    private LoginUseCase loginUseCase;

    @Autowired
    private RefreshTokenUseCase refreshTokenUseCase;

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

    @MockBean
    private EmailService emailService;

    private Person persistPerson(String username, String email, String rawPassword, AuthProvider authProvider) {
        Address address = new Address();
        entityManager.persist(address);

        Person person = new Person();
        person.setUsername(username);
        person.setEmail(email);
        person.setPassword(rawPassword == null ? null : passwordEncoder.encode(rawPassword));
        person.setAuthProvider(authProvider);
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        entityManager.persist(person);
        entityManager.flush();
        return person;
    }

    private PasswordResetToken persistResetToken(Person person, String rawCode, LocalDateTime expiresAt, boolean used) {
        PasswordResetToken token = new PasswordResetToken();
        token.setPerson(person);
        token.setTokenHash(TokenHashUtil.sha256Hex(rawCode));
        token.setExpiresAt(expiresAt);
        token.setUsed(used);
        token.setAttempts(0);
        token.setCreatedAt(LocalDateTime.now());
        entityManager.persist(token);
        entityManager.flush();
        return token;
    }

    @Test
    void requestPasswordResetWithExistingEmailGeneratesAValidCode() {
        Person person = persistPerson("resetuser1", "resetuser1@example.com", "correcta123", AuthProvider.LOCAL);

        requestPasswordResetUseCase.requestReset("resetuser1@example.com");

        List<PasswordResetToken> tokens = passwordResetTokenRepository.findAll();
        assertThat(tokens).hasSize(1);
        assertThat(tokens.get(0).getPerson().getId()).isEqualTo(person.getId());
        assertThat(tokens.get(0).getUsed()).isFalse();
        assertThat(tokens.get(0).getAttempts()).isZero();
        assertThat(tokens.get(0).getExpiresAt()).isAfter(LocalDateTime.now());
    }

    @Test
    void requestPasswordResetWithNonExistentEmailCreatesNothingAndDoesNotFail() {
        requestPasswordResetUseCase.requestReset("no-existe@example.com");

        assertThat(passwordResetTokenRepository.findAll()).isEmpty();
    }

    @Test
    void fullFlowValidCodeThenResetPasswordChangesThePasswordAndAllowsLoggingInWithTheNewOne() {
        Person person = persistPerson("resetuser2", "resetuser2@example.com", "vieja123", AuthProvider.LOCAL);
        persistResetToken(person, "123456", LocalDateTime.now().plusMinutes(15), false);

        String exchangeToken = verifyResetCodeUseCase.verify(new VerifyResetCodeCommand("resetuser2@example.com", "123456"));
        resetPasswordUseCase.reset(new ResetPasswordCommand(exchangeToken, "Nueva123"));

        AuthTokens tokens = loginUseCase.login(new LoginCommand("resetuser2", "Nueva123", "127.0.0.1"));
        assertThat(tokens.accessToken()).isNotBlank();
        assertThatThrownBy(() -> loginUseCase.login(new LoginCommand("resetuser2", "vieja123", "127.0.0.1")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void fullFlowOfAGoogleRegisteredPersonWithoutPasswordAlsoGivesLocalAccess() {
        Person person = persistPerson("googleuser1", "googleuser1@example.com", null, AuthProvider.GOOGLE);
        persistResetToken(person, "654321", LocalDateTime.now().plusMinutes(15), false);

        String exchangeToken = verifyResetCodeUseCase.verify(new VerifyResetCodeCommand("googleuser1@example.com", "654321"));
        resetPasswordUseCase.reset(new ResetPasswordCommand(exchangeToken, "Nueva123"));

        AuthTokens tokens = loginUseCase.login(new LoginCommand("googleuser1", "Nueva123", "127.0.0.1"));
        assertThat(tokens.accessToken()).isNotBlank();
    }

    @Test
    void verifyResetCodeWithAlreadyUsedCodeFails() {
        Person person = persistPerson("resetuser3", "resetuser3@example.com", "correcta123", AuthProvider.LOCAL);
        persistResetToken(person, "111111", LocalDateTime.now().plusMinutes(15), true);

        assertThatThrownBy(() -> verifyResetCodeUseCase.verify(new VerifyResetCodeCommand("resetuser3@example.com", "111111")))
                .isInstanceOf(InvalidPasswordResetTokenException.class);
    }

    @Test
    void verifyResetCodeWithExpiredCodeFails() {
        Person person = persistPerson("resetuser4", "resetuser4@example.com", "correcta123", AuthProvider.LOCAL);
        persistResetToken(person, "222222", LocalDateTime.now().minusMinutes(1), false);

        assertThatThrownBy(() -> verifyResetCodeUseCase.verify(new VerifyResetCodeCommand("resetuser4@example.com", "222222")))
                .isInstanceOf(InvalidPasswordResetTokenException.class);
    }

    @Test
    void verifyResetCodeWithIncorrectCodeIncrementsAttemptsAndReturnsNoToken() {
        Person person = persistPerson("resetuser8", "resetuser8@example.com", "correcta123", AuthProvider.LOCAL);
        PasswordResetToken token = persistResetToken(person, "333333", LocalDateTime.now().plusMinutes(15), false);

        assertThatThrownBy(() -> verifyResetCodeUseCase.verify(new VerifyResetCodeCommand("resetuser8@example.com", "000000")))
                .isInstanceOf(InvalidPasswordResetTokenException.class);

        PasswordResetToken reloaded = passwordResetTokenRepository.findById(token.getId()).orElseThrow();
        assertThat(reloaded.getAttempts()).isEqualTo(1);
        assertThat(reloaded.getUsed()).isFalse();
        assertThat(reloaded.getExchangeTokenHash()).isNull();
    }

    @Test
    void verifyResetCodeWithFiveIncorrectAttemptsInvalidatesTheCodeEvenIfTheSixthIsCorrect() {
        Person person = persistPerson("resetuser9", "resetuser9@example.com", "correcta123", AuthProvider.LOCAL);
        persistResetToken(person, "444444", LocalDateTime.now().plusMinutes(15), false);

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> verifyResetCodeUseCase.verify(new VerifyResetCodeCommand("resetuser9@example.com", "000000")))
                    .isInstanceOf(InvalidPasswordResetTokenException.class);
        }

        assertThatThrownBy(() -> verifyResetCodeUseCase.verify(new VerifyResetCodeCommand("resetuser9@example.com", "444444")))
                .isInstanceOf(InvalidPasswordResetTokenException.class);
    }

    @Test
    void resetPasswordWithInvalidExchangeTokenFails() {
        assertThatThrownBy(() -> resetPasswordUseCase.reset(new ResetPasswordCommand("token-que-no-existe", "Nueva123")))
                .isInstanceOf(InvalidPasswordResetTokenException.class);
    }

    @Test
    void resetPasswordWithTheSameExchangeTokenTwiceFailsTheSecond() {
        Person person = persistPerson("resetuser10", "resetuser10@example.com", "correcta123", AuthProvider.LOCAL);
        persistResetToken(person, "888888", LocalDateTime.now().plusMinutes(15), false);

        String exchangeToken = verifyResetCodeUseCase.verify(new VerifyResetCodeCommand("resetuser10@example.com", "888888"));
        resetPasswordUseCase.reset(new ResetPasswordCommand(exchangeToken, "Nueva123"));

        assertThatThrownBy(() -> resetPasswordUseCase.reset(new ResetPasswordCommand(exchangeToken, "otraNueva123")))
                .isInstanceOf(InvalidPasswordResetTokenException.class);
    }

    @Test
    void resetPasswordWithTooShortPasswordFails() {
        Person person = persistPerson("resetuser5", "resetuser5@example.com", "correcta123", AuthProvider.LOCAL);
        persistResetToken(person, "555555", LocalDateTime.now().plusMinutes(15), false);
        String exchangeToken = verifyResetCodeUseCase.verify(new VerifyResetCodeCommand("resetuser5@example.com", "555555"));

        assertThatThrownBy(() -> resetPasswordUseCase.reset(new ResetPasswordCommand(exchangeToken, "abc12")))
                .isInstanceOf(InvalidPasswordPolicyException.class);
    }

    @Test
    void resetPasswordWithoutDigitFails() {
        Person person = persistPerson("resetuser6", "resetuser6@example.com", "correcta123", AuthProvider.LOCAL);
        persistResetToken(person, "666666", LocalDateTime.now().plusMinutes(15), false);
        String exchangeToken = verifyResetCodeUseCase.verify(new VerifyResetCodeCommand("resetuser6@example.com", "666666"));

        assertThatThrownBy(() -> resetPasswordUseCase.reset(new ResetPasswordCommand(exchangeToken, "soloLetras")))
                .isInstanceOf(InvalidPasswordPolicyException.class);
    }

    @Test
    void resetPasswordWithInvalidPasswordDoesNotConsumeTheExchangeToken() {
        Person person = persistPerson("resetuser7", "resetuser7@example.com", "correcta123", AuthProvider.LOCAL);
        persistResetToken(person, "777777", LocalDateTime.now().plusMinutes(15), false);
        String exchangeToken = verifyResetCodeUseCase.verify(new VerifyResetCodeCommand("resetuser7@example.com", "777777"));

        assertThatThrownBy(() -> resetPasswordUseCase.reset(new ResetPasswordCommand(exchangeToken, "corta")))
                .isInstanceOf(InvalidPasswordPolicyException.class);

        resetPasswordUseCase.reset(new ResetPasswordCommand(exchangeToken, "Nueva123"));
        AuthTokens tokens = loginUseCase.login(new LoginCommand("resetuser7", "Nueva123", "127.0.0.1"));
        assertThat(tokens.accessToken()).isNotBlank();
    }

    @Test
    void passwordResetRevokesAllActiveSessions() {
        Person person = persistPerson("sesiones1", "sesiones1@example.com", "correcta123", AuthProvider.LOCAL);
        persistResetToken(person, "999999", LocalDateTime.now().plusMinutes(15), false);

        AuthTokens tokens = loginUseCase.login(new LoginCommand("sesiones1", "correcta123", "127.0.0.1"));

        String exchangeToken = verifyResetCodeUseCase.verify(new VerifyResetCodeCommand("sesiones1@example.com", "999999"));
        resetPasswordUseCase.reset(new ResetPasswordCommand(exchangeToken, "Nueva123"));

        assertThatThrownBy(() -> refreshTokenUseCase.refresh(tokens.refreshToken()))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }
}
