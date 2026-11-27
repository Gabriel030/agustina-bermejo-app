package com.red_escuela.backend.academicstructure.cohort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.red_escuela.backend.academicstructure.domain.CohortIntake;
import com.red_escuela.backend.academicstructure.dto.CreateProgramTermCohortRequest;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.dto.ProgramTermCohortResponse;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.exception.ProgramCohortAlreadyExistsException;
import com.red_escuela.backend.academicstructure.usecase.cohort.CreateProgramTermCohortUseCase;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.platform.infrastructure.FeatureSnapshotHolder;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.testsupport.TermFixtures;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class ProgramCohortWithBillingOffIT extends AbstractIntegrationTest {

    @Autowired private CreateProgramTermCohortUseCase createProgramTermCohortUseCase;
    @Autowired private TransactionTemplate transactions;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private FeatureSnapshotHolder snapshotHolder;

    @AfterEach
    void restoreBillingFull() {
        SecurityContextHolder.clearContext();
        jdbc.update("update platform_feature set enabled = true, mode = 'FULL', version = 0 where code = 'BILLING'");
        snapshotHolder.refresh();
    }

    @Test
    void cohortIsCreatedWithoutBillingTemplateWhenBillingIsOff() {
        switchBillingTo("OFF", false);
        Long[] ids = persistProgramTermAndActor();

        ProgramTermCohortResponse cohort = createProgramTermCohortUseCase.createProgramTermCohort(ids[0], ids[1], request(CohortIntake.FIRST));

        assertThat(cohort.id()).isNotNull();
        assertThat(jdbc.queryForObject("select template_id from program_cohort where id = ?", Long.class, cohort.id())).isNull();
        assertThat(jdbc.queryForObject("select program_id from program_cohort where id = ?", Long.class, cohort.id())).isEqualTo(ids[0]);
        assertThat(jdbc.queryForObject("select term_id from program_cohort where id = ?", Long.class, cohort.id())).isEqualTo(ids[1]);
        assertThat(jdbc.queryForObject("select count(*) from program_year_billing_template where program_id = ?", Integer.class, ids[0])).isZero();
    }

    @Test
    void secondCohortOfSameProgramTermAndIntakeIsRejected() {
        switchBillingTo("OFF", false);
        Long[] ids = persistProgramTermAndActor();
        createProgramTermCohortUseCase.createProgramTermCohort(ids[0], ids[1], request(CohortIntake.FIRST));

        assertThatThrownBy(() -> createProgramTermCohortUseCase.createProgramTermCohort(ids[0], ids[1], request(CohortIntake.FIRST)))
                .isInstanceOf(ProgramCohortAlreadyExistsException.class);
        assertThat(createProgramTermCohortUseCase.createProgramTermCohort(ids[0], ids[1], request(CohortIntake.SECOND)).id()).isNotNull();
    }

    private static CreateProgramTermCohortRequest request(CohortIntake intake) {
        return new CreateProgramTermCohortRequest(intake, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 2, 1), LocalDate.of(2027, 3, 1));
    }

    private void switchBillingTo(String mode, boolean enabled) {
        jdbc.update("update platform_feature set enabled = ?, mode = ? where code = 'BILLING'", enabled, mode);
        snapshotHolder.refresh();
    }

    private Long[] persistProgramTermAndActor() {
        return transactions.execute(status -> {
            Address address = new Address();
            entityManager.persist(address);
            Person person = new Person();
            person.setName("Persona");
            person.setLastname("Cohorte");
            String dni = "9" + System.nanoTime();
            person.setDni(dni);
            person.setUsername(dni);
            person.setEmail(dni + "@example.com");
            person.setAddress(address);
            person.setStatus(StatusType.ACTIVE);
            entityManager.persist(person);
            Program program = new Program();
            program.setName("Carrera " + dni);
            program.setTotalYears(3);
            program.setTotalCreditsRequired(100);
            entityManager.persist(program);
            Term term = TermFixtures.newTerm();
            entityManager.persist(term);
            SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(person.getId().toString(), null, "ROLE_ADMINISTRATIVO"));
            return new Long[] {program.getId(), term.getId()};
        });
    }
}
