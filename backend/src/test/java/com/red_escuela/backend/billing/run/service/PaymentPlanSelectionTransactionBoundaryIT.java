package com.red_escuela.backend.billing.run.service;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.dto.SelectPaymentPlanRequest;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.entity.PaymentPlanOption;
import com.red_escuela.backend.billing.invoice.operation.IssuingScenarioFactory;
import com.red_escuela.backend.billing.operation.CohortPromotionAcquirer;
import com.red_escuela.backend.billing.service.PaymentPlanSelectionInvoiceIssuingService;
import com.red_escuela.backend.billing.usecase.paymentplan.SelectPaymentPlanForCourseUseCase;
import com.red_escuela.backend.billing.usecase.paymentplan.SelectPaymentPlanForProgramUseCase;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.testsupport.AtomicityTestSupport;
import com.red_escuela.backend.testsupport.TermFixtures;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest
@ActiveProfiles("test")
class PaymentPlanSelectionTransactionBoundaryIT extends AbstractIntegrationTest {

    @Autowired
    private SelectPaymentPlanForCourseUseCase selectForCourse;

    @Autowired
    private SelectPaymentPlanForProgramUseCase selectForProgram;

    @Autowired
    private CohortPromotionAcquirer cohortPromotionAcquirer;

    @Autowired
    private PaymentPlanSelectionInvoiceIssuingService invoiceIssuingService;

    @Autowired
    private IssuingScenarioFactory factory;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private IssuingScenarioFactory.Offering offering;

    @BeforeEach
    void authenticateAsDirector() {
        offering = factory.newProgram(Currency.ARS, 80000, 50000);
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(offering.directorId().toString(), null, "ROLE_DIRECTOR"));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void courseSelectionFailingWhileAcquiringThePromotionLeavesNoSelection() {
        CourseScenario scenario = newCourseScenario();
        AtomicityTestSupport support = AtomicityTestSupport.observing(jdbcTemplate, "payment_plan_selection");

        support.assertNothingPersistedWhenFailingAtStep(1, steps -> {
            Mockito.doAnswer(invocation -> {
                steps.run(() -> { });
                return null;
            }).when(cohortPromotionAcquirer).acquireWhenEligible(any(CourseEnrollment.class), any());
            selectForCourse.select(scenario.enrollmentId(), new SelectPaymentPlanRequest(scenario.optionId(), null));
        });

        assertThat(selectionsOfCourseEnrollment(scenario.enrollmentId())).isZero();
    }

    @Test
    void courseSelectionWithoutFailuresPersistsTheSelection() {
        CourseScenario scenario = newCourseScenario();

        selectForCourse.select(scenario.enrollmentId(), new SelectPaymentPlanRequest(scenario.optionId(), null));

        assertThat(selectionsOfCourseEnrollment(scenario.enrollmentId())).isEqualTo(1);
    }

    @Test
    void firstProgramSelectionFailingWhileIssuingInvoicesLeavesNoSelection() {
        IssuingScenarioFactory.Learner learner = factory.newLearner(offering, null);
        jdbcTemplate.update("delete from payment_plan_selection where id = ?", learner.selectionId());
        Long optionId = newProgramOption();
        AtomicityTestSupport support = AtomicityTestSupport.observing(jdbcTemplate, "payment_plan_selection", "invoice");

        support.assertNothingPersistedWhenFailingAtStep(1, steps -> {
            Mockito.doAnswer(invocation -> {
                steps.run(() -> { });
                return null;
            }).when(invoiceIssuingService).issueInvoicesForNewPaymentPlan(any(), any(), any());
            selectForProgram.select(learner.programEnrollmentId(), new SelectPaymentPlanRequest(optionId, null));
        });

        assertThat(selectionsOfProgramEnrollment(learner.programEnrollmentId())).isZero();
    }

    @Test
    void programPlanChangeClosesThePreviousSelectionAndPersistsTheNewOne() {
        IssuingScenarioFactory.Learner learner = factory.newLearner(offering, null);
        Long optionId = newProgramOption();

        selectForProgram.select(learner.programEnrollmentId(), new SelectPaymentPlanRequest(optionId, null));

        assertThat(selectionsOfProgramEnrollment(learner.programEnrollmentId())).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from payment_plan_selection where program_enrollment_id = ? and effective_to is null",
                Long.class, learner.programEnrollmentId())).isEqualTo(1);
    }

    private record CourseScenario(Long enrollmentId, Long optionId) {
    }

    private CourseScenario newCourseScenario() {
        Long studentId = factory.newStudent(offering);
        return transactionTemplate.execute(status -> {
            Term term = TermFixtures.newTerm();
            term.setYear(2027);
            term.setDescription("2027-1C " + System.nanoTime());
            term.setStatus(StatusType.ACTIVE);
            term.setStartDate(LocalDateTime.of(2027, 3, 1, 0, 0));
            term.setEndDate(LocalDateTime.of(2027, 12, 31, 0, 0));
            entityManager.persist(term);
            Course course = new Course();
            course.setTerm(term);
            course.setName("Curso " + System.nanoTime());
            course.setIsActive(StatusType.ACTIVE);
            course.setCurrency(Currency.ARS);
            course.setDurationMonths(10);
            entityManager.persist(course);
            CourseEnrollment enrollment = new CourseEnrollment();
            enrollment.setStudent(studentRepository.findById(studentId).orElseThrow());
            enrollment.setCourse(course);
            enrollment.setStatus(EnrollmentStatus.ENROLLED);
            enrollment.setEnrollmentDate(LocalDateTime.of(2027, 2, 1, 10, 0));
            entityManager.persist(enrollment);
            PaymentPlanOption option = new PaymentPlanOption();
            option.setCourse(course);
            option.setDescription("Mensual");
            option.setCadence(PaymentPlanCadence.MONTHLY);
            option.setCreatedBy(entityManager.getReference(Person.class, offering.directorId()));
            entityManager.persist(option);
            return new CourseScenario(enrollment.getId(), option.getId());
        });
    }

    private Long newProgramOption() {
        return transactionTemplate.execute(status -> {
            PaymentPlanOption option = new PaymentPlanOption();
            option.setProgram(entityManager.getReference(Program.class, offering.programId()));
            option.setDescription("Otro plan");
            option.setCadence(PaymentPlanCadence.MONTHLY);
            option.setCreatedBy(entityManager.getReference(Person.class, offering.directorId()));
            entityManager.persist(option);
            return option.getId();
        });
    }

    private long selectionsOfCourseEnrollment(Long enrollmentId) {
        return jdbcTemplate.queryForObject("select count(*) from payment_plan_selection where course_enrollment_id = ?", Long.class, enrollmentId);
    }

    private long selectionsOfProgramEnrollment(Long enrollmentId) {
        return jdbcTemplate.queryForObject("select count(*) from payment_plan_selection where program_enrollment_id = ?", Long.class, enrollmentId);
    }
}
