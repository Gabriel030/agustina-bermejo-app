package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.billing.support.ListenerAtomicityFixtures;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.events.ProgramEnrollmentGraduatedEvent;
import com.red_escuela.backend.identity.entity.Student;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.time.YearMonth;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class GraduationBillingNotifierAtomicityIT extends AbstractIntegrationTest {

    private static final String ORIGINAL_NAME = "Original institution";

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private ListenerAtomicityFixtures fixtures;
    private Student student;
    private ProgramEnrollment enrollment;

    @BeforeEach
    void seedAGraduatingEnrollmentWithAnActiveDiscount() {
        fixtures = new ListenerAtomicityFixtures(entityManager, jdbcTemplate, transactionManager);
        student = fixtures.seedStudent();
        enrollment = fixtures.seedProgramEnrollment(student);
        BillingTestFixtures.invoice().forProgramEnrollment(enrollment).forPeriod(YearMonth.of(2027, 3))
                .withBaseAmount(ars(100000)).withScholarshipOf(ars(50000)).build();
        fixtures.inOneTransaction(() -> fixtures.renameInstitutionOf(student, ORIGINAL_NAME));
    }

    @AfterEach
    void removeDiscountEndSabotage() {
        fixtures.removeFailure();
    }

    @Test
    void billingFailureWhileRegisteringTheGraduationRevertsWhatThePublisherWrote() {
        fixtures.failOn("student_discount", "update", "old.status = 'ACTIVE' and new.status <> 'ACTIVE'", "discount end failed");

        assertThatThrownBy(() -> fixtures.inOneTransaction(() -> {
            fixtures.renameInstitutionOf(student, "Written by the publisher");
            eventPublisher.publishEvent(graduationEvent());
        })).rootCause().hasMessageContaining("discount end failed");

        assertThat(fixtures.institutionNameOf(student)).isEqualTo(ORIGINAL_NAME);
        assertThat(activeDiscountsOf(enrollment.getId())).isEqualTo(1);
    }

    @Test
    void successfulGraduationRegistrationCommitsTogetherWithWhatThePublisherWrote() {
        fixtures.inOneTransaction(() -> {
            fixtures.renameInstitutionOf(student, "Written by the publisher");
            eventPublisher.publishEvent(graduationEvent());
        });

        assertThat(fixtures.institutionNameOf(student)).isEqualTo("Written by the publisher");
        assertThat(activeDiscountsOf(enrollment.getId())).isZero();
    }

    private ProgramEnrollmentGraduatedEvent graduationEvent() {
        return ProgramEnrollmentGraduatedEvent.graduatedOn(student.getId(), enrollment.getId(), LocalDate.now());
    }

    private Integer activeDiscountsOf(Long programEnrollmentId) {
        return jdbcTemplate.queryForObject("select count(*) from student_discount where program_enrollment_id = ? and status = 'ACTIVE'", Integer.class, programEnrollmentId);
    }
}
