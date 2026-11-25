package com.red_escuela.backend.billing.invoice.service;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.billing.support.ListenerAtomicityFixtures;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.events.CourseEnrollmentConfirmedByAdmissionPaymentEvent;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class CourseAdmissionInvoiceEnrollmentLinkerAtomicityIT extends AbstractIntegrationTest {

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
    private CourseEnrollment courseEnrollment;
    private Long invoiceId;

    @BeforeEach
    void seedAnAdmissionInvoiceWithoutEnrollment() {
        fixtures = new ListenerAtomicityFixtures(entityManager, jdbcTemplate, transactionManager);
        student = fixtures.seedStudent();
        courseEnrollment = fixtures.seedCourseEnrollment(student);
        invoiceId = BillingTestFixtures.invoice().forStudent(student).ofType(FeeType.ADMINISTRATIVE_FEE).build().getId();
        fixtures.inOneTransaction(() -> fixtures.renameInstitutionOf(student, ORIGINAL_NAME));
    }

    @AfterEach
    void removeLinkSabotage() {
        fixtures.removeFailure();
    }

    @Test
    void linkFailureRevertsWhatThePublisherWroteInTheSameTransaction() {
        fixtures.failOn("invoice_line", "update", "new.course_enrollment_id is not null", "invoice link failed");

        assertThatThrownBy(() -> fixtures.inOneTransaction(() -> {
            fixtures.renameInstitutionOf(student, "Written by the publisher");
            eventPublisher.publishEvent(CourseEnrollmentConfirmedByAdmissionPaymentEvent.confirmedFor(invoiceId, courseEnrollment.getId()));
        })).rootCause().hasMessageContaining("invoice link failed");

        assertThat(fixtures.institutionNameOf(student)).isEqualTo(ORIGINAL_NAME);
        assertThat(enrollmentIdsOfLinesOf(invoiceId)).containsOnlyNulls();
    }

    @Test
    void successfulLinkCommitsTogetherWithWhatThePublisherWrote() {
        fixtures.inOneTransaction(() -> {
            fixtures.renameInstitutionOf(student, "Written by the publisher");
            eventPublisher.publishEvent(CourseEnrollmentConfirmedByAdmissionPaymentEvent.confirmedFor(invoiceId, courseEnrollment.getId()));
        });

        assertThat(fixtures.institutionNameOf(student)).isEqualTo("Written by the publisher");
        assertThat(enrollmentIdsOfLinesOf(invoiceId)).containsOnly(courseEnrollment.getId());
    }

    private List<Long> enrollmentIdsOfLinesOf(Long invoiceId) {
        return jdbcTemplate.queryForList("select course_enrollment_id from invoice_line where invoice_id = ?", Long.class, invoiceId);
    }
}
