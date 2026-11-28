package com.red_escuela.backend.academicstructure.cohort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.red_escuela.backend.academicstructure.domain.CohortIntake;
import com.red_escuela.backend.academicstructure.domain.CohortReadinessItemCode;
import com.red_escuela.backend.academicstructure.domain.CurricularTerm;
import com.red_escuela.backend.academicstructure.dto.CohortReadinessItemResponse;
import com.red_escuela.backend.academicstructure.dto.CohortReadinessResponse;
import com.red_escuela.backend.academicstructure.dto.CreateProgramTermCohortRequest;
import com.red_escuela.backend.academicstructure.dto.ProgramTermCohortResponse;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.exception.ProgramCohortAlreadyOpenException;
import com.red_escuela.backend.academicstructure.exception.ProgramCohortNotReadyException;
import com.red_escuela.backend.academicstructure.usecase.cohort.CreateProgramTermCohortUseCase;
import com.red_escuela.backend.academicstructure.usecase.cohort.GetProgramCohortReadinessUseCase;
import com.red_escuela.backend.academicstructure.usecase.cohort.OpenProgramCohortUseCase;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.exception.CohortResponsibleRequiredException;
import com.red_escuela.backend.identity.exception.PreceptorRoleRequiredException;
import com.red_escuela.backend.platform.infrastructure.FeatureSnapshotHolder;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.testsupport.AcademicReproductionFixtures;
import com.red_escuela.backend.testsupport.HolidayFixtures;
import com.red_escuela.backend.testsupport.PreceptorTestFactory;
import com.red_escuela.backend.testsupport.TermFixtures;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;

@SpringBootTest
@ActiveProfiles("test")
class ProgramCohortReadinessIT extends AbstractIntegrationTest {

    private static final int HOLIDAYLESS_YEAR = 2097;

    @Autowired private CreateProgramTermCohortUseCase createCohort;
    @Autowired private OpenProgramCohortUseCase openCohort;
    @Autowired private GetProgramCohortReadinessUseCase readiness;
    @Autowired private com.red_escuela.backend.academicstructure.usecase.readiness.GetIncompleteAcademicOfferUseCase incompleteOffer;
    @Autowired private TransactionTemplate transactions;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private FeatureSnapshotHolder snapshotHolder;
    @Autowired private PreceptorTestFactory preceptors;

    @AfterEach
    void restoreBillingFull() {
        SecurityContextHolder.clearContext();
        jdbc.update("update platform_feature set enabled = true, mode = 'FULL', version = 0 where code = 'BILLING'");
        snapshotHolder.refresh();
    }

    @Test
    void cohortCreationRequiresAtLeastOneResponsiblePreceptor() {
        switchBillingTo("OFF", false);
        Long[] ids = persistProgramTermAndActor(2026);

        assertThatThrownBy(() -> createCohort.createProgramTermCohort(ids[0], ids[1], request(List.of())))
                .isInstanceOf(CohortResponsibleRequiredException.class);
        assertThatThrownBy(() -> createCohort.createProgramTermCohort(ids[0], ids[1], request(null)))
                .isInstanceOf(CohortResponsibleRequiredException.class);
        assertThat(jdbc.queryForObject("select count(*) from program_cohort where program_id = ?", Integer.class, ids[0])).isZero();
    }

    @Test
    void cohortCreationRejectsAResponsibleWithoutThePreceptorRole() {
        switchBillingTo("OFF", false);
        Long[] ids = persistProgramTermAndActor(2026);
        Long teacher = preceptors.createPersonWithRole("DOCENTE");

        assertThatThrownBy(() -> createCohort.createProgramTermCohort(ids[0], ids[1], request(List.of(teacher))))
                .isInstanceOf(PreceptorRoleRequiredException.class);
        assertThat(jdbc.queryForObject("select count(*) from program_cohort where program_id = ?", Integer.class, ids[0])).isZero();
    }

    @Test
    void cohortCreationStoresTheResponsiblePreceptorsAndStartsClosed() {
        switchBillingTo("OFF", false);
        Long[] ids = persistProgramTermAndActor(2026);
        Long first = preceptors.createPreceptorId();
        Long second = preceptors.createPreceptorId();

        ProgramTermCohortResponse cohort = createCohort.createProgramTermCohort(ids[0], ids[1], request(List.of(first, second, first)));

        assertThat(cohort.openedAt()).isNull();
        assertThat(jdbc.queryForList("select person_id from preceptor_assignment where program_cohort_id = ?", Long.class, cohort.id()))
                .containsExactlyInAnyOrder(first, second);
    }

    @Test
    void openingFailsListingEveryMissingBlockingItem() {
        switchBillingTo("OFF", false);
        Long[] ids = persistProgramTermAndActor(HOLIDAYLESS_YEAR);
        Long cohortId = createCohort.createProgramTermCohort(ids[0], ids[1], request(List.of(preceptors.createPreceptorId()))).id();

        assertThatThrownBy(() -> openCohort.openProgramCohort(cohortId))
                .isInstanceOfSatisfying(ProgramCohortNotReadyException.class,
                        exception -> assertThat(exception.details()).containsEntry("missingItems", List.of(CohortReadinessItemCode.HOLIDAYS_LOADED.name())));
        assertThat(jdbc.queryForObject("select opened_at from program_cohort where id = ?", java.sql.Timestamp.class, cohortId)).isNull();
    }

    @Test
    void incompleteOfferListsACohortWithMissingItemsAndItsReasonsAndSkipsAReadyOne() {
        switchBillingTo("OFF", false);
        Long[] incompleteIds = persistProgramTermAndActor(HOLIDAYLESS_YEAR);
        Long incompleteCohortId = createCohort.createProgramTermCohort(incompleteIds[0], incompleteIds[1], request(List.of(preceptors.createPreceptorId()))).id();
        Long[] readyIds = persistProgramTermAndActor(2026);
        Long readyCohortId = createCohort.createProgramTermCohort(readyIds[0], readyIds[1], request(List.of(preceptors.createPreceptorId()))).id();

        var offer = incompleteOffer.getIncompleteAcademicOffer();

        assertThat(offer.cohorts()).filteredOn(item -> item.id().equals(incompleteCohortId)).singleElement().satisfies(item -> {
            assertThat(item.missingCodes()).containsExactly(CohortReadinessItemCode.HOLIDAYS_LOADED.name());
            assertThat(item.reasons()).hasSize(1);
        });
        assertThat(offer.cohorts()).noneMatch(item -> item.id().equals(readyCohortId));
        assertThat(offer.incompleteCohortCount()).isEqualTo(offer.cohorts().size());
    }

    @Test
    void openingSucceedsWhenEveryBlockingItemIsFulfilledAndAReopenIsRejected() {
        switchBillingTo("OFF", false);
        Long[] ids = persistProgramTermAndActor(2026);
        Long cohortId = createCohort.createProgramTermCohort(ids[0], ids[1], request(List.of(preceptors.createPreceptorId()))).id();

        ProgramTermCohortResponse opened = openCohort.openProgramCohort(cohortId);

        assertThat(opened.openedAt()).isNotNull();
        assertThat(readiness.getProgramCohortReadiness(cohortId).open()).isTrue();
        assertThatThrownBy(() -> openCohort.openProgramCohort(cohortId)).isInstanceOf(ProgramCohortAlreadyOpenException.class);
    }

    @Test
    void firstYearSubjectWithoutAnApprovedSectionBlocksOpening() {
        switchBillingTo("OFF", false);
        Long[] ids = persistProgramTermAndActor(2026);
        transactions.executeWithoutResult(status -> {
            AcademicReproductionFixtures fixtures = new AcademicReproductionFixtures(entityManager);
            fixtures.programSubject(entityManager.find(Program.class, ids[0]), fixtures.subject("Álgebra"), 1, CurricularTerm.FIRST);
        });
        Long cohortId = createCohort.createProgramTermCohort(ids[0], ids[1], request(List.of(preceptors.createPreceptorId()))).id();

        CohortReadinessItemResponse item = item(readiness.getProgramCohortReadiness(cohortId), CohortReadinessItemCode.FIRST_YEAR_SECTIONS);

        assertThat(item.fulfilled()).isFalse();
        assertThat(item.reasons()).containsExactly("Álgebra: sin comisión aprobada en el período");
        assertThatThrownBy(() -> openCohort.openProgramCohort(cohortId)).isInstanceOf(ProgramCohortNotReadyException.class);
    }

    @Test
    void firstYearSectionReportsEveryMissingRequirementAndTheMissingPreceptor() {
        switchBillingTo("OFF", false);
        Long[] ids = persistProgramTermAndActor(2026);
        transactions.executeWithoutResult(status -> {
            AcademicReproductionFixtures fixtures = new AcademicReproductionFixtures(entityManager);
            Subject subject = fixtures.subject("Análisis");
            fixtures.programSubject(entityManager.find(Program.class, ids[0]), subject, 1, CurricularTerm.FIRST);
            SubjectSection section = new SubjectSection();
            section.setName("Com A");
            section.setSubject(subject);
            section.setTerm(entityManager.find(Term.class, ids[1]));
            section.setStatus(StatusType.ACTIVE);
            entityManager.persist(section);
        });
        Long cohortId = createCohort.createProgramTermCohort(ids[0], ids[1], request(List.of(preceptors.createPreceptorId()))).id();

        CohortReadinessResponse result = readiness.getProgramCohortReadiness(cohortId);

        assertThat(item(result, CohortReadinessItemCode.FIRST_YEAR_SECTIONS).reasons())
                .containsExactly("Análisis (Com A): falta titular, cupo, horario y aula, evaluación aprobada");
        assertThat(item(result, CohortReadinessItemCode.SECTION_PRECEPTORS).reasons()).containsExactly("Análisis (Com A): sin preceptor");
        assertThat(result.ready()).isFalse();
    }

    @Test
    void cohortResponsibleItemIsInformativeAndNeverBlocksOpening() {
        switchBillingTo("OFF", false);
        Long[] ids = persistProgramTermAndActor(2026);
        Long cohortId = createCohort.createProgramTermCohort(ids[0], ids[1], request(List.of(preceptors.createPreceptorId()))).id();
        jdbc.update("delete from preceptor_assignment where program_cohort_id = ?", cohortId);

        CohortReadinessResponse result = readiness.getProgramCohortReadiness(cohortId);

        CohortReadinessItemResponse responsible = item(result, CohortReadinessItemCode.COHORT_RESPONSIBLE);
        assertThat(responsible.fulfilled()).isFalse();
        assertThat(responsible.blocking()).isFalse();
        assertThat(result.ready()).isTrue();
    }

    @Test
    void billingTemplateItemIsOnlyPresentAndBlockingWhenBillingIsFull() {
        switchBillingTo("OFF", false);
        Long[] ids = persistProgramTermAndActor(2026);
        Long cohortId = createCohort.createProgramTermCohort(ids[0], ids[1], request(List.of(preceptors.createPreceptorId()))).id();

        assertThat(readiness.getProgramCohortReadiness(cohortId).items()).noneMatch(i -> i.code() == CohortReadinessItemCode.BILLING_TEMPLATE);

        switchBillingTo("FULL", true);
        CohortReadinessResponse full = readiness.getProgramCohortReadiness(cohortId);
        assertThat(item(full, CohortReadinessItemCode.BILLING_TEMPLATE).fulfilled()).isFalse();
        assertThat(full.ready()).isFalse();
    }

    @Test
    void cohortWithoutOpenedAtDoesNotAdmitEvenInsideTheAdmissionDates() {
        com.red_escuela.backend.academicstructure.entity.ProgramCohort cohort = new com.red_escuela.backend.academicstructure.entity.ProgramCohort();
        cohort.setAdmissionStart(LocalDate.of(2027, 1, 1));
        cohort.setAdmissionEnd(LocalDate.of(2027, 2, 1));

        assertThat(cohort.isAdmissionOpenOn(LocalDate.of(2027, 1, 15))).isFalse();
        cohort.markOpened(java.time.LocalDateTime.of(2026, 12, 1, 0, 0));
        assertThat(cohort.isAdmissionOpenOn(LocalDate.of(2027, 1, 15))).isTrue();
        assertThat(cohort.isAdmissionOpenOn(LocalDate.of(2027, 2, 2))).isFalse();
    }

    private static CohortReadinessItemResponse item(CohortReadinessResponse response, CohortReadinessItemCode code) {
        return response.items().stream().filter(i -> i.code() == code).findFirst().orElseThrow();
    }

    private static CreateProgramTermCohortRequest request(List<Long> responsible) {
        return new CreateProgramTermCohortRequest(CohortIntake.FIRST, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 2, 1), LocalDate.of(2027, 3, 1), responsible);
    }

    private void switchBillingTo(String mode, boolean enabled) {
        jdbc.update("update platform_feature set enabled = ?, mode = ? where code = 'BILLING'", enabled, mode);
        snapshotHolder.refresh();
    }

    private Long[] persistProgramTermAndActor(int termYear) {
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
            term.setYear(termYear);
            entityManager.persist(term);
            if (termYear < HOLIDAYLESS_YEAR) {
                HolidayFixtures.ensureHolidaysLoaded(entityManager, termYear, termYear);
            }
            SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(person.getId().toString(), null, "ROLE_ADMINISTRATIVO"));
            return new Long[] {program.getId(), term.getId()};
        });
    }
}
