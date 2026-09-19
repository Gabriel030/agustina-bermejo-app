package com.red_escuela.backend.identity.usecase.auth;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.identity.entity.AuthProvider;
import com.red_escuela.backend.identity.entity.LoginAttempt;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.identity.exception.InvalidCredentialsException;
import com.red_escuela.backend.identity.exception.InvalidRefreshTokenException;
import com.red_escuela.backend.identity.repository.LoginAttemptRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthFlowTest extends AbstractIntegrationTest {
    @Autowired
    private LoginUseCase loginUseCase;

    @Autowired
    private RefreshTokenUseCase refreshTokenUseCase;

    @Autowired
    private LogoutUseCase logoutUseCase;

    @Autowired
    private LoginAttemptRepository loginAttemptRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    @Value("${auth.lockout.max-attempts}")
    private int lockoutMaxAttempts;

    private Person persistPerson(String username, String rawPassword, AuthProvider provider) {
        Address address = new Address();
        entityManager.persist(address);

        Person person = new Person();
        person.setUsername(username);
        person.setEmail(username + "@example.com");
        person.setPassword(rawPassword == null ? null : passwordEncoder.encode(rawPassword));
        person.setAuthProvider(provider);
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        entityManager.persist(person);
        entityManager.flush();
        return person;
    }

    @Test
    void loginWithCorrectPasswordReturnsValidTokensAndRecordsSuccessfulAttempt() {
        persistPerson("docente1", "correcta123", AuthProvider.LOCAL);

        AuthTokens tokens = loginUseCase.login(new LoginCommand("docente1", "correcta123", "127.0.0.1"));

        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.refreshToken()).isNotBlank();
        assertThat(tokens.expiresIn()).isPositive();

        List<LoginAttempt> attempts = loginAttemptRepository.findAll();
        assertThat(attempts).hasSize(1);
        assertThat(attempts.get(0).getSuccessful()).isTrue();
        assertThat(attempts.get(0).getIpAddress()).isEqualTo("127.0.0.1");
    }

    @Test
    void loginWithIncorrectPasswordThrowsTheSameExceptionAsNonExistentUser() {
        persistPerson("docente2", "correcta123", AuthProvider.LOCAL);

        assertThatThrownBy(() -> loginUseCase.login(new LoginCommand("docente2", "incorrecta", "127.0.0.1")))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThatThrownBy(() -> loginUseCase.login(new LoginCommand("no-existe", "cualquiera", "127.0.0.1")))
                .isInstanceOf(InvalidCredentialsException.class);

        List<LoginAttempt> attempts = loginAttemptRepository.findAll();
        assertThat(attempts).hasSize(2);
        assertThat(attempts).allMatch(a -> !a.getSuccessful());
    }

    @Test
    void loginOnGooglePersonWithoutPasswordIsRejected() {
        persistPerson("googleuser", null, AuthProvider.GOOGLE);

        assertThatThrownBy(() -> loginUseCase.login(new LoginCommand("googleuser", "cualquiera", "127.0.0.1")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginOnGooglePersonWithPasswordSetWorks() {
        persistPerson("googleuser2", "correcta123", AuthProvider.GOOGLE);

        AuthTokens tokens = loginUseCase.login(new LoginCommand("googleuser2", "correcta123", "127.0.0.1"));

        assertThat(tokens.accessToken()).isNotBlank();
    }

    @Test
    void repeatedLoginWithIncorrectPasswordLocksTheAccountAndACorrectPasswordIsAlsoRejectedWhileTheLockLasts() {
        persistPerson("bloqueable", "correcta123", AuthProvider.LOCAL);

        for (int i = 0; i < lockoutMaxAttempts; i++) {
            assertThatThrownBy(() -> loginUseCase.login(new LoginCommand("bloqueable", "mal", "127.0.0.1")))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        assertThatThrownBy(() -> loginUseCase.login(new LoginCommand("bloqueable", "correcta123", "127.0.0.1")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void refreshWithValidTokenRotatesAndTheOldTokenStopsWorking() {
        persistPerson("refrescable", "correcta123", AuthProvider.LOCAL);
        AuthTokens original = loginUseCase.login(new LoginCommand("refrescable", "correcta123", "127.0.0.1"));

        AuthTokens rotated = refreshTokenUseCase.refresh(original.refreshToken());

        assertThat(rotated.refreshToken()).isNotEqualTo(original.refreshToken());
        assertThatThrownBy(() -> refreshTokenUseCase.refresh(original.refreshToken()))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void logoutInvalidaElRefreshToken() {
        persistPerson("desloguable", "correcta123", AuthProvider.LOCAL);
        AuthTokens tokens = loginUseCase.login(new LoginCommand("desloguable", "correcta123", "127.0.0.1"));

        logoutUseCase.logout(tokens.refreshToken());

        assertThatThrownBy(() -> refreshTokenUseCase.refresh(tokens.refreshToken()))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }
}
