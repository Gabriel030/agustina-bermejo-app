package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.AuthProvider;
import com.red_escuela.backend.identity.entity.PasswordResetToken;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.exception.InvalidCredentialsException;
import com.red_escuela.backend.identity.specialist.SessionRevoker;
import com.red_escuela.backend.identity.usecase.auth.LoginCommand;
import com.red_escuela.backend.identity.usecase.auth.LoginUseCase;
import com.red_escuela.backend.identity.usecase.auth.ResetPasswordCommand;
import com.red_escuela.backend.identity.usecase.auth.ResetPasswordUseCase;
import com.red_escuela.backend.identity.util.TokenHashUtil;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.time.ApplicationTime;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

@SpringBootTest
@ActiveProfiles("test")
class AuthTransactionBoundaryIT extends AbstractIntegrationTest {

    private static final String USERNAME = "tx.boundary";
    private static final String PASSWORD = "Correcta123";
    private static final String EXCHANGE_TOKEN = "exchange-token-boundary";

    @Autowired
    private LoginUseCase loginUseCase;

    @Autowired
    private ResetPasswordUseCase resetPasswordUseCase;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoSpyBean
    private SessionRevoker sessionRevoker;

    @AfterEach
    void cleanUp() {
        reset(sessionRevoker);
        jdbcTemplate.update("delete from login_attempt where attempted_username = ?", USERNAME);
        jdbcTemplate.update("delete from password_reset_token where person_id in (select id from person where username = ?)", USERNAME);
        jdbcTemplate.update("delete from person_role where person_id in (select id from person where username = ?)", USERNAME);
        jdbcTemplate.update("delete from person where username = ?", USERNAME);
    }

    private Long persistPerson() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            Address address = new Address();
            entityManager.persist(address);
            Person person = new Person();
            person.setName("Nombre");
            person.setLastname("Apellido");
            person.setUsername(USERNAME);
            person.setDni("79990001");
            person.setEmail(USERNAME + "@example.com");
            person.setPassword(passwordEncoder.encode(PASSWORD));
            person.setAuthProvider(AuthProvider.LOCAL);
            person.setAddress(address);
            person.setStatus(StatusType.ACTIVE);
            entityManager.persist(person);
            entityManager.flush();
            return person.getId();
        });
    }

    private void persistExchangeToken(Long personId) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            PasswordResetToken token = new PasswordResetToken();
            token.setPerson(entityManager.getReference(Person.class, personId));
            token.setTokenHash("unused-code-hash");
            token.setExpiresAt(ApplicationTime.now().plusMinutes(30));
            token.setCreatedAt(ApplicationTime.now());
            token.setUsed(true);
            token.setExchangeTokenHash(TokenHashUtil.sha256Hex(EXCHANGE_TOKEN));
            token.setExchangeExpiresAt(ApplicationTime.now().plusMinutes(30));
            entityManager.persist(token);
        });
    }

    @Test
    void failedLoginStillPersistsTheAttemptAndTheFailureCounter() {
        Long personId = persistPerson();

        assertThatThrownBy(() -> loginUseCase.login(new LoginCommand(USERNAME, "Incorrecta1", "127.0.0.1")))
                .isInstanceOf(InvalidCredentialsException.class);

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from login_attempt where attempted_username = ? and successful = false",
                Integer.class, USERNAME)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select failed_login_attempts from person where id = ?", Integer.class, personId)).isEqualTo(1);
    }

    @Test
    void passwordResetFailingWhileRevokingSessionsKeepsPasswordAndExchangeTokenUntouched() {
        Long personId = persistPerson();
        persistExchangeToken(personId);
        String passwordBefore = jdbcTemplate.queryForObject("select password from person where id = ?", String.class, personId);
        doAnswer(invocation -> {
            throw new IllegalStateException("session revocation failed");
        }).when(sessionRevoker).revokeAllSessions(any());

        assertThatThrownBy(() -> resetPasswordUseCase.reset(new ResetPasswordCommand(EXCHANGE_TOKEN, "NuevaClave456")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(jdbcTemplate.queryForObject("select password from person where id = ?", String.class, personId))
                .isEqualTo(passwordBefore);
        assertThat(jdbcTemplate.queryForObject(
                "select exchange_token_hash from password_reset_token where person_id = ?", String.class, personId))
                .isEqualTo(TokenHashUtil.sha256Hex(EXCHANGE_TOKEN));
    }
}
