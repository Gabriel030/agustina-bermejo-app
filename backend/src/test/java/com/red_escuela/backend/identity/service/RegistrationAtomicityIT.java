package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.dto.AuthTokens;
import com.red_escuela.backend.identity.dto.RegisterRequest;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.specialist.AuthTokenIssuer;
import com.red_escuela.backend.identity.specialist.LegalRegistrationCompleter;
import com.red_escuela.backend.identity.usecase.auth.RegisterUseCase;
import com.red_escuela.backend.testsupport.AtomicityTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

@SpringBootTest
@ActiveProfiles("test")
class RegistrationAtomicityIT extends AbstractIntegrationTest {

    private static final String USERNAME = "atomic.register";

    @Autowired
    private RegisterUseCase registerUseCase;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoSpyBean
    private LegalRegistrationCompleter legalRegistrationCompleter;

    @MockitoSpyBean
    private AuthTokenIssuer authTokenIssuer;

    private Long institutionId;

    private AtomicityTestSupport support;

    @BeforeEach
    void createInstitution() {
        institutionId = new TransactionTemplate(transactionManager).execute(status -> {
            Institution institution = new Institution();
            institution.setName("Instituto de atomicidad");
            entityManager.persist(institution);
            entityManager.flush();
            return institution.getId();
        });
        support = AtomicityTestSupport.observing(jdbcTemplate, "person", "student", "legal_acceptance",
                "newsletter_preference", "contact_consent", "refresh_token");
    }

    @AfterEach
    void cleanUp() {
        reset(legalRegistrationCompleter, authTokenIssuer);
        String personIds = "select id from person where username = ?";
        jdbcTemplate.update("delete from refresh_token where person_id in (" + personIds + ")", USERNAME);
        jdbcTemplate.update("delete from legal_acceptance where person_id in (" + personIds + ")", USERNAME);
        jdbcTemplate.update("delete from newsletter_preference where person_id in (" + personIds + ")", USERNAME);
        jdbcTemplate.update("delete from contact_consent where person_id in (" + personIds + ")", USERNAME);
        jdbcTemplate.update("delete from student where person_id in (" + personIds + ")", USERNAME);
        jdbcTemplate.update("delete from person_role where person_id in (" + personIds + ")", USERNAME);
        jdbcTemplate.update("delete from person where username = ?", USERNAME);
        jdbcTemplate.update("delete from institution where id = ?", institutionId);
    }

    private RegisterRequest request() {
        return new RegisterRequest("Ana", "Perez", USERNAME + "@example.com", USERNAME, "Password123", institutionId,
                true, true, LocalDate.of(2000, 1, 15), null, null, null, true, true, null);
    }

    @Test
    void successfulRegistrationWritesPersonStudentAndLegalAcceptance() {
        AuthTokens tokens = registerUseCase.register(request());

        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(jdbcTemplate.queryForObject("select count(*) from person where username = ?", Integer.class, USERNAME))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from student where person_id in (select id from person where username = ?)",
                Integer.class, USERNAME)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from legal_acceptance where person_id in (select id from person where username = ?)",
                Integer.class, USERNAME)).isPositive();
    }

    @Test
    void registrationFailingRightAfterTheLegalAcceptanceLeavesNoPersonStudentOrAcceptance() {
        support.assertNothingPersistedWhenFailingAtStep(1, steps -> {
            doAnswer(invocation -> {
                Object result = invocation.callRealMethod();
                steps.run(() -> { });
                return result;
            }).when(legalRegistrationCompleter).complete(any(Person.class), any(RegisterRequest.class));

            registerUseCase.register(request());
        });
    }

    @Test
    void registrationFailingWhileIssuingTokensLeavesNoPersonStudentOrAcceptance() {
        support.assertNothingPersistedWhenFailingAtStep(1, steps -> {
            doAnswer(invocation -> {
                steps.run(() -> { });
                return invocation.callRealMethod();
            }).when(authTokenIssuer).issueTokens(any(Person.class));

            registerUseCase.register(request());
        });
    }
}
