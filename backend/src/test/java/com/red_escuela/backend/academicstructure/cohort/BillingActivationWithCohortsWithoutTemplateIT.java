package com.red_escuela.backend.academicstructure.cohort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.red_escuela.backend.academicstructure.domain.CohortIntake;
import com.red_escuela.backend.academicstructure.dto.CreateProgramTermCohortRequest;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.usecase.cohort.CreateProgramTermCohortUseCase;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.platform.domain.BillingModeChangeClearance;
import com.red_escuela.backend.platform.domain.FeatureChange;
import com.red_escuela.backend.platform.domain.FeatureTransitionRequest;
import com.red_escuela.backend.platform.exception.BillingActivationBlockedByCohortsWithoutTemplateException;
import com.red_escuela.backend.platform.infrastructure.FeatureSnapshotHolder;
import com.red_escuela.backend.platform.model.FeatureCode;
import com.red_escuela.backend.platform.usecase.ChangePlatformFeatureUseCase;
import com.red_escuela.backend.platform.usecase.PreviewPlatformFeatureChangeUseCase;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.module.BillingMode;
import com.red_escuela.backend.testsupport.PreceptorTestFactory;
import com.red_escuela.backend.testsupport.TermFixtures;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
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
class BillingActivationWithCohortsWithoutTemplateIT extends AbstractIntegrationTest {

    @Autowired private CreateProgramTermCohortUseCase createProgramTermCohortUseCase;
    @Autowired private ChangePlatformFeatureUseCase changeFeature;
    @Autowired private PreviewPlatformFeatureChangeUseCase previewFeatureChange;
    @Autowired private TransactionTemplate transactions;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private FeatureSnapshotHolder snapshotHolder;
    @Autowired private PreceptorTestFactory preceptorTestFactory;

    @AfterEach
    void restoreBillingFull() {
        SecurityContextHolder.clearContext();
        jdbc.update("delete from program_cohort where template_id is null");
        jdbc.update("delete from feature_change_log");
        jdbc.update("update platform_feature set enabled = true, mode = 'FULL', version = 0 where code = 'BILLING'");
        snapshotHolder.refresh();
    }

    @Test
    void offToMirrorAndFullAreBlockedWhileCohortsWithoutTemplateExist() {
        switchBillingToOff();
        Long cohortId = createCohortWithoutTemplate();

        assertThatThrownBy(() -> changeFeature.change(change(BillingMode.MIRROR)))
                .isInstanceOf(BillingActivationBlockedByCohortsWithoutTemplateException.class)
                .hasMessageContaining("sin plantilla de facturación");
        assertThatThrownBy(() -> changeFeature.change(change(BillingMode.FULL)))
                .isInstanceOf(BillingActivationBlockedByCohortsWithoutTemplateException.class);
        assertThatThrownBy(() -> previewFeatureChange.preview(new FeatureTransitionRequest(FeatureCode.BILLING, BillingMode.FULL)))
                .isInstanceOf(BillingActivationBlockedByCohortsWithoutTemplateException.class);

        assertThat(jdbc.queryForObject("select mode from platform_feature where code = 'BILLING'", String.class)).isEqualTo("OFF");
        assertThat(jdbc.queryForObject("select count(*) from feature_change_log", Integer.class)).isZero();
        assertThat(cohortId).isNotNull();
    }

    @Test
    void offToMirrorIsAllowedOnceNoCohortWithoutTemplateRemains() {
        switchBillingToOff();
        Long cohortId = createCohortWithoutTemplate();
        jdbc.update("delete from program_cohort where id = ?", cohortId);

        changeFeature.change(change(BillingMode.MIRROR));

        assertThat(jdbc.queryForObject("select mode from platform_feature where code = 'BILLING'", String.class)).isEqualTo("MIRROR");
    }

    private FeatureChange change(BillingMode mode) {
        return new FeatureChange(FeatureCode.BILLING, null, mode, null, "Activación de prueba", BillingModeChangeClearance.NONE);
    }

    private void switchBillingToOff() {
        jdbc.update("update platform_feature set enabled = false, mode = 'OFF' where code = 'BILLING'");
        snapshotHolder.refresh();
    }

    private Long createCohortWithoutTemplate() {
        Long[] ids = persistProgramTermAndActor();
        return createProgramTermCohortUseCase.createProgramTermCohort(ids[0], ids[1],
                new CreateProgramTermCohortRequest(CohortIntake.FIRST, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 2, 1), LocalDate.of(2027, 3, 1),
                        List.of(preceptorTestFactory.createPreceptorId()))).id();
    }

    private Long[] persistProgramTermAndActor() {
        return transactions.execute(status -> {
            Address address = new Address();
            entityManager.persist(address);
            Person person = new Person();
            person.setName("Persona");
            person.setLastname("Activacion");
            String dni = "8" + System.nanoTime();
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
