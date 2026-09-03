package com.red_escuela.backend.service;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.entity.AuthProvider;
import com.red_escuela.backend.entity.LoginAttempt;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.exception.InvalidCredentialsException;
import com.red_escuela.backend.exception.InvalidRefreshTokenException;
import com.red_escuela.backend.repository.LoginAttemptRepository;
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
class AuthServiceTest extends AbstractIntegrationTest {

    @Autowired
    private AuthService authService;

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
    void loginConPasswordCorrectaDevuelveTokensValidosYRegistraAttemptExitoso() {
        persistPerson("docente1", "correcta123", AuthProvider.LOCAL);

        AuthTokens tokens = authService.login("docente1", "correcta123", "127.0.0.1");

        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.refreshToken()).isNotBlank();
        assertThat(tokens.expiresIn()).isPositive();

        List<LoginAttempt> attempts = loginAttemptRepository.findAll();
        assertThat(attempts).hasSize(1);
        assertThat(attempts.get(0).getSuccessful()).isTrue();
        assertThat(attempts.get(0).getIpAddress()).isEqualTo("127.0.0.1");
    }

    @Test
    void loginConPasswordIncorrectaLanzaLaMismaExcepcionQueUsuarioInexistente() {
        persistPerson("docente2", "correcta123", AuthProvider.LOCAL);

        assertThatThrownBy(() -> authService.login("docente2", "incorrecta", "127.0.0.1"))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThatThrownBy(() -> authService.login("no-existe", "cualquiera", "127.0.0.1"))
                .isInstanceOf(InvalidCredentialsException.class);

        List<LoginAttempt> attempts = loginAttemptRepository.findAll();
        assertThat(attempts).hasSize(2);
        assertThat(attempts).allMatch(a -> !a.getSuccessful());
    }

    // Épica 1 Fase 4: authProvider dejó de gatear login() — el gate real es tener
    // password seteado o no. Una Person de alta Google que consiguió password (por
    // ejemplo vía "olvidé mi contraseña") entra local igual, ver [[Spec técnica -
    // Recuperar contraseña]].
    @Test
    void loginSobrePersonaGoogleSinPasswordRechaza() {
        persistPerson("googleuser", null, AuthProvider.GOOGLE);

        assertThatThrownBy(() -> authService.login("googleuser", "cualquiera", "127.0.0.1"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginSobrePersonaGoogleConPasswordSeteadaFunciona() {
        persistPerson("googleuser2", "correcta123", AuthProvider.GOOGLE);

        AuthTokens tokens = authService.login("googleuser2", "correcta123", "127.0.0.1");

        assertThat(tokens.accessToken()).isNotBlank();
    }

    @Test
    void loginRepetidoConPasswordIncorrectaBloqueaLaCuentaYUnaPasswordCorrectaTambienRechazaMientrasDureElBloqueo() {
        persistPerson("bloqueable", "correcta123", AuthProvider.LOCAL);

        for (int i = 0; i < lockoutMaxAttempts; i++) {
            assertThatThrownBy(() -> authService.login("bloqueable", "mal", "127.0.0.1"))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        assertThatThrownBy(() -> authService.login("bloqueable", "correcta123", "127.0.0.1"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void refreshConTokenValidoRotaYElTokenViejoDejaDeServir() {
        persistPerson("refrescable", "correcta123", AuthProvider.LOCAL);
        AuthTokens original = authService.login("refrescable", "correcta123", "127.0.0.1");

        AuthTokens rotated = authService.refresh(original.refreshToken());

        assertThat(rotated.refreshToken()).isNotEqualTo(original.refreshToken());
        assertThatThrownBy(() -> authService.refresh(original.refreshToken()))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void logoutInvalidaElRefreshToken() {
        persistPerson("desloguable", "correcta123", AuthProvider.LOCAL);
        AuthTokens tokens = authService.login("desloguable", "correcta123", "127.0.0.1");

        authService.logout(tokens.refreshToken());

        assertThatThrownBy(() -> authService.refresh(tokens.refreshToken()))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }
}
