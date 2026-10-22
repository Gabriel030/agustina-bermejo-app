package com.red_escuela.backend.billing.discount.operation;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.discount.DiscountDraftBuilder;
import com.red_escuela.backend.billing.dto.SelectPaymentPlanRequest;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.entity.PaymentPlanOption;
import com.red_escuela.backend.billing.usecase.paymentplan.SelectPaymentPlanForCourseUseCase;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-240")
@Tag("D-295")
class AcquireCohortPromotionOnPaymentPlanSelectionIT extends AbstractCohortPromotionAcquisitionIT {

    @Autowired
    private SelectPaymentPlanForCourseUseCase selectPaymentPlan;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private StudentRepository studentRepository;

    private record CourseScenario(Long courseId, Long enrollmentId, Long monthlyOptionId) {
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    private CourseScenario newCourseScenario() {
        Long studentId = factory.newStudent(offering);
        return transactionTemplate.execute(status -> {
            Term term = new Term();
            term.setYear(2027);
            term.setDescription("2027-1C " + System.nanoTime());
            term.setStatus(StatusType.ACTIVE);
            term.setStartDate(LocalDateTime.of(2027, 3, 1, 0, 0));
            term.setEndDate(LocalDateTime.of(2027, 12, 31, 0, 0));
            entityManager.persist(term);
            Course course = new Course();
            course.setTerm(term);
            course.setName("Curso con promoción " + System.nanoTime());
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
            option.setCreatedBy(entityManager.getReference(com.red_escuela.backend.identity.entity.Person.class, director.getId()));
            entityManager.persist(option);
            return new CourseScenario(course.getId(), enrollment.getId(), option.getId());
        });
    }

    private void authenticateAsDirector() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(director.getId().toString(), null, "ROLE_DIRECTOR"));
    }

    private DiscountDraftBuilder coursePromotion(CourseScenario scenario, String name, String percent) {
        return promotion(name, percent, 2).forCourse(scenario.courseId());
    }

    private long acquisitionsOfCourseEnrollment(CourseScenario scenario) {
        return jdbc.queryForObject("select count(*) from student_discount where course_enrollment_id = ? and origin = 'COHORT_ACQUISITION'", Long.class, scenario.enrollmentId());
    }

    private long selectionsOf(CourseScenario scenario) {
        return jdbc.queryForObject("select count(*) from payment_plan_selection where course_enrollment_id = ?", Long.class, scenario.enrollmentId());
    }

    @Test
    void choosingTheMonthlyPlanOfACourseAcquiresItsMostFavorablePromotionInTheSameTransaction() {
        CourseScenario scenario = newCourseScenario();
        Long best = publish(coursePromotion(scenario, "Curso 20", "20"));
        Long other = publish(coursePromotion(scenario, "Curso 10", "10"));
        authenticateAsDirector();
        assertThat(acquisitionsOfCourseEnrollment(scenario)).isZero();
        assertThat(selectionsOf(scenario)).isZero();

        selectPaymentPlan.select(scenario.enrollmentId(), new SelectPaymentPlanRequest(scenario.monthlyOptionId(), null));

        assertThat(selectionsOf(scenario)).isEqualTo(1);
        Map<String, Object> acquired = jdbc.queryForMap("select discount_definition_id, origin, discount_type, status from student_discount where course_enrollment_id = ?",
                scenario.enrollmentId());
        assertThat(acquired).containsEntry("discount_definition_id", best).containsEntry("origin", "COHORT_ACQUISITION").containsEntry("discount_type", "COHORT_PROMOTION")
                .containsEntry("status", "ACTIVE");
        assertThat(placesUsed(other)).isZero();
    }

    @Test
    void rollingBackTheOuterTransactionUndoesTheAcquisitionTogetherWithTheSelection() {
        CourseScenario scenario = newCourseScenario();
        Long definitionId = publish(coursePromotion(scenario, "Curso 10", "10"));
        authenticateAsDirector();

        transactionTemplate.executeWithoutResult(status -> {
            selectPaymentPlan.select(scenario.enrollmentId(), new SelectPaymentPlanRequest(scenario.monthlyOptionId(), null));
            assertThat(acquisitionsOfCourseEnrollment(scenario)).isEqualTo(1);
            status.setRollbackOnly();
        });

        assertThat(acquisitionsOfCourseEnrollment(scenario)).isZero();
        assertThat(selectionsOf(scenario)).isZero();
        assertThat(placesUsed(definitionId)).isZero();
        assertThat(quotaExhaustionWasNotified(definitionId)).isFalse();
    }

    @Test
    void aPromotionOfAnotherCourseIsNotAcquiredWhenChoosingThePlan() {
        CourseScenario scenario = newCourseScenario();
        CourseScenario otherCourse = newCourseScenario();
        Long definitionId = publish(coursePromotion(otherCourse, "Otro curso", "30"));
        authenticateAsDirector();

        selectPaymentPlan.select(scenario.enrollmentId(), new SelectPaymentPlanRequest(scenario.monthlyOptionId(), null));

        assertThat(selectionsOf(scenario)).isEqualTo(1);
        assertThat(acquisitionsOfCourseEnrollment(scenario)).isZero();
        assertThat(placesUsed(definitionId)).isZero();
    }
}
