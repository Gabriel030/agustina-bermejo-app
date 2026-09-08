package com.red_escuela.backend.service;
import com.red_escuela.backend.identity.service.AuthTokens;

import com.red_escuela.backend.identity.service.AuthService;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.identity.entity.AuthProvider;
import com.red_escuela.backend.identity.entity.PasswordResetToken;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.identity.exception.InvalidCredentialsException;
import com.red_escuela.backend.identity.exception.InvalidPasswordPolicyException;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * RF-AUTH-03: código de recuperación de 6 dígitos, universal para cualquier authProvider,
 * en dos pasos — verifyResetCode(email, code) da un token de intercambio de un solo uso,
 * y recién con ese token resetPassword(resetToken, newPassword) cambia la contraseña.
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
    void requestPasswordResetConEmailExistenteGeneraUnCodigoValidoYLoManda() {
        Person person = persistPerson("resetuser1", "resetuser1@example.com", "correcta123", AuthProvider.LOCAL);

        authService.requestPasswordReset("resetuser1@example.com");

        List<PasswordResetToken> tokens = passwordResetTokenRepository.findAll();
        assertThat(tokens).hasSize(1);
        assertThat(tokens.get(0).getPerson().getId()).isEqualTo(person.getId());
        assertThat(tokens.get(0).getUsed()).isFalse();
        assertThat(tokens.get(0).getAttempts()).isZero();
        assertThat(tokens.get(0).getExpiresAt()).isAfter(LocalDateTime.now());
        verify(emailService).sendPasswordResetCode(eq(person), anyString());
    }

    @Test
    void requestPasswordResetConEmailInexistenteNoCreaNadaYNoFalla() {
        authService.requestPasswordReset("no-existe@example.com");

        assertThat(passwordResetTokenRepository.findAll()).isEmpty();
    }

    @Test
    void flujoCompletoCodigoValidoLuegoResetPasswordCambiaLaPasswordYPermiteLoguearseConLaNueva() {
        Person person = persistPerson("resetuser2", "resetuser2@example.com", "vieja123", AuthProvider.LOCAL);
        persistResetToken(person, "123456", LocalDateTime.now().plusMinutes(15), false);

        String exchangeToken = authService.verifyResetCode("resetuser2@example.com", "123456");
        authService.resetPassword(exchangeToken, "Nueva123");

        AuthTokens tokens = authService.login("resetuser2", "Nueva123", "127.0.0.1");
        assertThat(tokens.accessToken()).isNotBlank();
        assertThatThrownBy(() -> authService.login("resetuser2", "vieja123", "127.0.0.1"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void flujoCompletoDeUnaPersonaDeAltaGoogleSinPasswordLeDaAccesoLocalTambien() {
        Person person = persistPerson("googleuser1", "googleuser1@example.com", null, AuthProvider.GOOGLE);
        persistResetToken(person, "654321", LocalDateTime.now().plusMinutes(15), false);

        String exchangeToken = authService.verifyResetCode("googleuser1@example.com", "654321");
        authService.resetPassword(exchangeToken, "Nueva123");

        AuthTokens tokens = authService.login("googleuser1", "Nueva123", "127.0.0.1");
        assertThat(tokens.accessToken()).isNotBlank();
    }

    @Test
    void verifyResetCodeConCodigoYaUsadoFalla() {
        Person person = persistPerson("resetuser3", "resetuser3@example.com", "correcta123", AuthProvider.LOCAL);
        persistResetToken(person, "111111", LocalDateTime.now().plusMinutes(15), true);

        assertThatThrownBy(() -> authService.verifyResetCode("resetuser3@example.com", "111111"))
                .isInstanceOf(InvalidPasswordResetTokenException.class);
    }

    @Test
    void verifyResetCodeConCodigoExpiradoFalla() {
        Person person = persistPerson("resetuser4", "resetuser4@example.com", "correcta123", AuthProvider.LOCAL);
        persistResetToken(person, "222222", LocalDateTime.now().minusMinutes(1), false);

        assertThatThrownBy(() -> authService.verifyResetCode("resetuser4@example.com", "222222"))
                .isInstanceOf(InvalidPasswordResetTokenException.class);
    }

    @Test
    void verifyResetCodeConCodigoIncorrectoIncrementaIntentosYNoDevuelveToken() {
        Person person = persistPerson("resetuser8", "resetuser8@example.com", "correcta123", AuthProvider.LOCAL);
        PasswordResetToken token = persistResetToken(person, "333333", LocalDateTime.now().plusMinutes(15), false);

        assertThatThrownBy(() -> authService.verifyResetCode("resetuser8@example.com", "000000"))
                .isInstanceOf(InvalidPasswordResetTokenException.class);

        PasswordResetToken reloaded = passwordResetTokenRepository.findById(token.getId()).orElseThrow();
        assertThat(reloaded.getAttempts()).isEqualTo(1);
        assertThat(reloaded.getUsed()).isFalse();
        assertThat(reloaded.getExchangeTokenHash()).isNull();
    }

    @Test
    void verifyResetCodeConCincoIntentosIncorrectosInvalidaElCodigoAunSiElSextoEsCorrecto() {
        Person person = persistPerson("resetuser9", "resetuser9@example.com", "correcta123", AuthProvider.LOCAL);
        persistResetToken(person, "444444", LocalDateTime.now().plusMinutes(15), false);

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> authService.verifyResetCode("resetuser9@example.com", "000000"))
                    .isInstanceOf(InvalidPasswordResetTokenException.class);
        }

        assertThatThrownBy(() -> authService.verifyResetCode("resetuser9@example.com", "444444"))
                .isInstanceOf(InvalidPasswordResetTokenException.class);
    }

    @Test
    void resetPasswordConTokenDeIntercambioInvalidoFalla() {
        assertThatThrownBy(() -> authService.resetPassword("token-que-no-existe", "Nueva123"))
                .isInstanceOf(InvalidPasswordResetTokenException.class);
    }

    @Test
    void resetPasswordConElMismoTokenDeIntercambioDosVecesFallaLaSegunda() {
        Person person = persistPerson("resetuser10", "resetuser10@example.com", "correcta123", AuthProvider.LOCAL);
        persistResetToken(person, "888888", LocalDateTime.now().plusMinutes(15), false);

        String exchangeToken = authService.verifyResetCode("resetuser10@example.com", "888888");
        authService.resetPassword(exchangeToken, "Nueva123");

        assertThatThrownBy(() -> authService.resetPassword(exchangeToken, "otraNueva123"))
                .isInstanceOf(InvalidPasswordResetTokenException.class);
    }

    // RF-AUTH-05: política mínima configurable (auth.password.min-length=8,
    // auth.password.require-letter-and-digit=true en application.properties).
    @Test
    void resetPasswordConContraseñaMuyCortaFalla() {
        Person person = persistPerson("resetuser5", "resetuser5@example.com", "correcta123", AuthProvider.LOCAL);
        persistResetToken(person, "555555", LocalDateTime.now().plusMinutes(15), false);
        String exchangeToken = authService.verifyResetCode("resetuser5@example.com", "555555");

        assertThatThrownBy(() -> authService.resetPassword(exchangeToken, "abc12"))
                .isInstanceOf(InvalidPasswordPolicyException.class);
    }

    @Test
    void resetPasswordConContraseñaSinDigitoFalla() {
        Person person = persistPerson("resetuser6", "resetuser6@example.com", "correcta123", AuthProvider.LOCAL);
        persistResetToken(person, "666666", LocalDateTime.now().plusMinutes(15), false);
        String exchangeToken = authService.verifyResetCode("resetuser6@example.com", "666666");

        assertThatThrownBy(() -> authService.resetPassword(exchangeToken, "soloLetras"))
                .isInstanceOf(InvalidPasswordPolicyException.class);
    }

    @Test
    void resetPasswordConContraseñaInvalidaNoConsumeElTokenDeIntercambio() {
        Person person = persistPerson("resetuser7", "resetuser7@example.com", "correcta123", AuthProvider.LOCAL);
        persistResetToken(person, "777777", LocalDateTime.now().plusMinutes(15), false);
        String exchangeToken = authService.verifyResetCode("resetuser7@example.com", "777777");

        assertThatThrownBy(() -> authService.resetPassword(exchangeToken, "corta"))
                .isInstanceOf(InvalidPasswordPolicyException.class);

        // El mismo token de intercambio, con una contraseña que sí cumple la política,
        // tiene que seguir sirviendo.
        authService.resetPassword(exchangeToken, "Nueva123");
        AuthTokens tokens = authService.login("resetuser7", "Nueva123", "127.0.0.1");
        assertThat(tokens.accessToken()).isNotBlank();
    }
}
