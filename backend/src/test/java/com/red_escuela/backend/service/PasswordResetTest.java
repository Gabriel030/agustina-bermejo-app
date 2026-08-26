package com.red_escuela.backend.service;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.entity.AuthProvider;
import com.red_escuela.backend.entity.PasswordResetToken;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.exception.InvalidCredentialsException;
import com.red_escuela.backend.exception.InvalidPasswordPolicyException;
import com.red_escuela.backend.exception.InvalidPasswordResetTokenException;
import com.red_escuela.backend.repository.PasswordResetTokenRepository;
import com.red_escuela.backend.util.TokenHashUtil;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RF-AUTH-03 (parcial): estructura del token de recuperación, sin envío de email.
 * resetPassword se prueba con un token armado directamente en el test (raw + hash),
 * igual que lo tendría un cliente real que recibió el token por email — no depende de
 * requestPasswordReset, que solo loguea el valor crudo, nunca lo expone por otra vía.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PasswordResetTest extends AbstractIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    private Person persistPerson(String username, String email, String rawPassword) {
        Address address = new Address();
        entityManager.persist(address);

        Person person = new Person();
        person.setUsername(username);
        person.setEmail(email);
        person.setPassword(passwordEncoder.encode(rawPassword));
        person.setAuthProvider(AuthProvider.LOCAL);
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        entityManager.persist(person);
        entityManager.flush();
        return person;
    }

    private void persistResetToken(Person person, String rawToken, LocalDateTime expiresAt, boolean used) {
        PasswordResetToken token = new PasswordResetToken();
        token.setPerson(person);
        token.setTokenHash(TokenHashUtil.sha256Hex(rawToken));
        token.setExpiresAt(expiresAt);
        token.setUsed(used);
        token.setCreatedAt(LocalDateTime.now());
        entityManager.persist(token);
        entityManager.flush();
    }

    @Test
    void requestPasswordResetConEmailExistenteGeneraUnTokenValidoEnLaTabla() {
        Person person = persistPerson("resetuser1", "resetuser1@example.com", "correcta123");

        authService.requestPasswordReset("resetuser1@example.com");

        List<PasswordResetToken> tokens = passwordResetTokenRepository.findAll();
        assertThat(tokens).hasSize(1);
        assertThat(tokens.get(0).getPerson().getId()).isEqualTo(person.getId());
        assertThat(tokens.get(0).getUsed()).isFalse();
        assertThat(tokens.get(0).getExpiresAt()).isAfter(LocalDateTime.now());
    }

    @Test
    void requestPasswordResetConEmailInexistenteNoCreaNadaYNoFalla() {
        authService.requestPasswordReset("no-existe@example.com");

        assertThat(passwordResetTokenRepository.findAll()).isEmpty();
    }

    @Test
    void resetPasswordConTokenValidoCambiaLaPasswordYPermiteLoguearseConLaNueva() {
        Person person = persistPerson("resetuser2", "resetuser2@example.com", "vieja123");
        String rawToken = "raw-token-de-prueba";
        persistResetToken(person, rawToken, LocalDateTime.now().plusMinutes(60), false);

        authService.resetPassword(rawToken, "nueva123");

        AuthTokens tokens = authService.login("resetuser2", "nueva123", "127.0.0.1");
        assertThat(tokens.accessToken()).isNotBlank();
        assertThatThrownBy(() -> authService.login("resetuser2", "vieja123", "127.0.0.1"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void resetPasswordConTokenYaUsadoFalla() {
        Person person = persistPerson("resetuser3", "resetuser3@example.com", "correcta123");
        String rawToken = "raw-token-usado";
        persistResetToken(person, rawToken, LocalDateTime.now().plusMinutes(60), true);

        assertThatThrownBy(() -> authService.resetPassword(rawToken, "nueva123"))
                .isInstanceOf(InvalidPasswordResetTokenException.class);
    }

    @Test
    void resetPasswordConTokenExpiradoFalla() {
        Person person = persistPerson("resetuser4", "resetuser4@example.com", "correcta123");
        String rawToken = "raw-token-expirado";
        persistResetToken(person, rawToken, LocalDateTime.now().minusMinutes(1), false);

        assertThatThrownBy(() -> authService.resetPassword(rawToken, "nueva123"))
                .isInstanceOf(InvalidPasswordResetTokenException.class);
    }

    // RF-AUTH-05: política mínima configurable (auth.password.min-length=8,
    // auth.password.require-letter-and-digit=true en application.properties).
    @Test
    void resetPasswordConContraseñaMuyCortaFalla() {
        Person person = persistPerson("resetuser5", "resetuser5@example.com", "correcta123");
        String rawToken = "raw-token-corta";
        persistResetToken(person, rawToken, LocalDateTime.now().plusMinutes(60), false);

        assertThatThrownBy(() -> authService.resetPassword(rawToken, "abc12"))
                .isInstanceOf(InvalidPasswordPolicyException.class);
    }

    @Test
    void resetPasswordConContraseñaSinDigitoFalla() {
        Person person = persistPerson("resetuser6", "resetuser6@example.com", "correcta123");
        String rawToken = "raw-token-sin-digito";
        persistResetToken(person, rawToken, LocalDateTime.now().plusMinutes(60), false);

        assertThatThrownBy(() -> authService.resetPassword(rawToken, "soloLetras"))
                .isInstanceOf(InvalidPasswordPolicyException.class);
    }

    @Test
    void resetPasswordConContraseñaInvalidaNoConsumeElToken() {
        Person person = persistPerson("resetuser7", "resetuser7@example.com", "correcta123");
        String rawToken = "raw-token-reintentable";
        persistResetToken(person, rawToken, LocalDateTime.now().plusMinutes(60), false);

        assertThatThrownBy(() -> authService.resetPassword(rawToken, "corta"))
                .isInstanceOf(InvalidPasswordPolicyException.class);

        // El mismo token, con una contraseña que sí cumple la política, tiene que seguir sirviendo.
        authService.resetPassword(rawToken, "nueva123");
        AuthTokens tokens = authService.login("resetuser7", "nueva123", "127.0.0.1");
        assertThat(tokens.accessToken()).isNotBlank();
    }
}
