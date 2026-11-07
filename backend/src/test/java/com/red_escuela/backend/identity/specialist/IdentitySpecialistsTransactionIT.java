package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.identity.entity.EmailVerificationToken;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.RefreshToken;
import com.red_escuela.backend.identity.exception.InvalidRefreshTokenException;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class IdentitySpecialistsTransactionIT extends AbstractIntegrationTest {

    @Autowired
    private SessionRevoker sessionRevoker;

    @Autowired
    private RefreshTokenRevoker refreshTokenRevocation;

    @Autowired
    private EmailVerificationConfirmer emailVerificationConfirmation;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Person person;
    private Person stranger;
    private Long tokenId;
    private String tokenHash;

    @BeforeEach
    void persistPersonWithRefreshToken() {
        String suffix = String.valueOf(System.nanoTime());
        tokenHash = "hash-" + suffix;
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            person = persistPerson("P" + suffix);
            stranger = persistPerson("S" + suffix);
            RefreshToken token = new RefreshToken();
            token.setPerson(person);
            token.setTokenHash(tokenHash);
            token.setExpiresAt(LocalDateTime.now().plusDays(1));
            token.setCreatedAt(LocalDateTime.now());
            entityManager.persist(token);
            tokenId = token.getId();
        });
    }

    @Test
    void revokeSessionMarksTheOwnTokenAsRevokedWithoutAnOuterTransaction() {
        sessionRevoker.revokeSession(tokenId, person);

        assertThat(isRevoked()).isTrue();
    }

    @Test
    void revokeSessionOfAnotherPersonFailsAndLeavesTheTokenActive() {
        assertThatThrownBy(() -> sessionRevoker.revokeSession(tokenId, stranger))
                .isInstanceOf(InvalidRefreshTokenException.class);

        assertThat(isRevoked()).isFalse();
    }

    @Test
    void revokeByHashMarksTheTokenAsRevoked() {
        refreshTokenRevocation.revokeByHash(tokenHash);

        assertThat(isRevoked()).isTrue();
    }

    @Test
    void confirmByTokenHashMarksEmailAsVerifiedAndConsumesTheToken() {
        String verificationHash = "verify-" + tokenHash;
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            EmailVerificationToken token = new EmailVerificationToken();
            token.setPerson(entityManager.find(Person.class, person.getId()));
            token.setTokenHash(verificationHash);
            token.setExpiresAt(LocalDateTime.now().plusDays(1));
            entityManager.persist(token);
        });
        assertThat(emailVerified()).isFalse();

        emailVerificationConfirmation.confirmByTokenHash(verificationHash);

        assertThat(emailVerified()).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from email_verification_token where token_hash = ?", Long.class, verificationHash)).isZero();
    }

    private boolean isRevoked() {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("select revoked from refresh_token where id = ?", Boolean.class, tokenId));
    }

    private boolean emailVerified() {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("select email_verified from person where id = ?", Boolean.class, person.getId()));
    }

    private Person persistPerson(String dni) {
        Address address = new Address();
        entityManager.persist(address);
        Person created = new Person();
        created.setName("Persona");
        created.setLastname(dni);
        created.setDni(dni);
        created.setUsername(dni);
        created.setEmail(dni + "@example.com");
        created.setAddress(address);
        created.setStatus(StatusType.ACTIVE);
        entityManager.persist(created);
        return created;
    }
}
