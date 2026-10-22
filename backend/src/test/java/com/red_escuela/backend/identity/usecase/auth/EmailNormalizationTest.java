package com.red_escuela.backend.identity.usecase.auth;

import com.red_escuela.backend.academicstructure.entity.Institution;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.identity.exception.UsernameAlreadyExistsException;
import com.red_escuela.backend.identity.dto.RegisterRequest;
import com.red_escuela.backend.identity.entity.AuthProvider;
import com.red_escuela.backend.identity.entity.PasswordResetToken;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.exception.InvalidPasswordResetTokenException;
import com.red_escuela.backend.identity.infrastructure.GoogleIdTokenService;
import com.red_escuela.backend.identity.repository.PasswordResetTokenRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.shared.domain.EmailAddress;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import com.red_escuela.backend.testsupport.SharedMockBeans;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@SharedMockBeans
@Transactional
class EmailNormalizationTest extends AbstractIntegrationTest {
    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private RequestPasswordResetUseCase requestPasswordResetUseCase;

    @Autowired
    private VerifyResetCodeUseCase verifyResetCodeUseCase;

    @Autowired
    private LoginWithGoogleUseCase loginWithGoogleUseCase;

    @Autowired
    private RegisterUseCase registerUseCase;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private GoogleIdTokenService googleIdTokenService;

    private Person persistPerson(String username, String email) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setUsername(username);
        person.setEmail(email);
        person.setAuthProvider(AuthProvider.LOCAL);
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        entityManager.persist(person);
        entityManager.flush();
        return person;
    }

    @Test
    void emailIsStoredLowercasedAndTrimmed() {
        Person person = persistPerson("norm1", "  Ana.Perez@Mail.com  ");
        entityManager.clear();

        String stored = jdbcTemplate.queryForObject("select email from person where id = ?", String.class, person.getId());
        assertThat(stored).isEqualTo("ana.perez@mail.com");
    }

    @Test
    void passwordResetFindsPersonWithDifferentCapitalization() {
        Person person = persistPerson("norm2", "Ana.Perez@Mail.com");

        requestPasswordResetUseCase.requestReset("ana.perez@mail.com");

        List<PasswordResetToken> tokens = passwordResetTokenRepository.findAll();
        assertThat(tokens).anyMatch(t -> t.getPerson().getId().equals(person.getId()));
    }

    @Test
    void verifyResetCodeFindsPersonWithDifferentCapitalization() {
        Person person = persistPerson("norm3", "Ana.Perez3@Mail.com");
        requestPasswordResetUseCase.requestReset("ANA.PEREZ3@mail.com");
        assertThat(passwordResetTokenRepository.findAll()).anyMatch(t -> t.getPerson().getId().equals(person.getId()));

        assertThatThrownBy(() -> verifyResetCodeUseCase.verify(new VerifyResetCodeCommand("ana.perez3@MAIL.com", "000000")))
                .isInstanceOf(InvalidPasswordResetTokenException.class);
        assertThat(personRepository.findByEmail(EmailAddress.of("ANA.PEREZ3@MAIL.COM"))).isPresent();
    }

    @Test
    void googleLoginFindsAccountStoredWithUppercase() {
        Person person = persistPerson("norm4", "Ana.Perez4@Mail.com");
        when(googleIdTokenService.verify("token")).thenReturn(
                new GoogleIdTokenService.GoogleProfile("sub-norm4", "ana.perez4@mail.com", "Ana", "Perez"));

        AuthTokens tokens = loginWithGoogleUseCase.login("token");

        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(personRepository.findById(person.getId()).orElseThrow().getGoogleSubjectId()).isEqualTo("sub-norm4");
    }

    @Test
    void registerWithEmailDifferingOnlyInCapitalizationIsRejectedAsConflict() {
        persistPerson("norm5", "dup@mail.com");
        Institution institution = new Institution();
        institution.setName("Instituto de prueba");
        entityManager.persist(institution);
        entityManager.flush();

        assertThatThrownBy(() -> registerUseCase.register(
                new RegisterRequest("Ana", "Perez", "DUP@mail.com", "norm5b", "Password123", institution.getId())))
                .isInstanceOf(UsernameAlreadyExistsException.class);
    }

    @Test
    void migrationNormalizesExistingEmails() throws Exception {
        Person person = persistPerson("norm7", "tmp7@mail.com");
        jdbcTemplate.update("update person set email = ? where id = ?", "  Ana.Mig@Mail.COM ", person.getId());

        String script = StreamUtils.copyToString(
                new ClassPathResource("db/migration/V79__normalize_person_email.sql").getInputStream(), StandardCharsets.UTF_8);
        jdbcTemplate.execute(script);

        String stored = jdbcTemplate.queryForObject("select email from person where id = ?", String.class, person.getId());
        assertThat(stored).isEqualTo("ana.mig@mail.com");
    }

    @Test
    void migrationTurnsBlankEmailIntoNull() throws Exception {
        Person person = persistPerson("norm8", "tmp8@mail.com");
        jdbcTemplate.update("update person set email = ? where id = ?", "   ", person.getId());

        runMigration();

        String stored = jdbcTemplate.queryForObject("select email from person where id = ?", String.class, person.getId());
        assertThat(stored).isNull();
    }

    @Test
    void migrationFailsWithClearMessageOnInvalidEmail() {
        Person person = persistPerson("norm9", "tmp9@mail.com");
        jdbcTemplate.update("update person set email = ? where id = ?", "sin-arroba", person.getId());

        assertThatThrownBy(this::runMigration)
                .hasStackTraceContaining("formato válido");
    }

    private void runMigration() {
        try {
            String script = StreamUtils.copyToString(
                    new ClassPathResource("db/migration/V79__normalize_person_email.sql").getInputStream(), StandardCharsets.UTF_8);
            jdbcTemplate.execute(script);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
