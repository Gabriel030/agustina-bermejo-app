package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.billing.invoice.domain.event.InvoiceCancelledEvent;
import com.red_escuela.backend.billing.invoice.domain.event.InvoiceDiscardedEvent;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.billing.support.ListenerAtomicityFixtures;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
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
class DiscountConsumptionListenersAtomicityIT extends AbstractIntegrationTest {

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
    private Long invoiceId;

    @BeforeEach
    void seedAnInvoiceWithALiveDiscount() {
        fixtures = new ListenerAtomicityFixtures(entityManager, jdbcTemplate, transactionManager);
        student = fixtures.seedStudent();
        ProgramEnrollment enrollment = fixtures.seedProgramEnrollment(student);
        invoiceId = BillingTestFixtures.invoice().forProgramEnrollment(enrollment).forPeriod(YearMonth.of(2027, 3))
                .withBaseAmount(ars(100000)).withScholarshipOf(ars(50000)).build().getId();
        fixtures.inOneTransaction(() -> fixtures.renameInstitutionOf(student, ORIGINAL_NAME));
    }

    @AfterEach
    void removeReleaseSabotage() {
        fixtures.removeFailure();
    }

    @Test
    void releaseFailureOnCancelledInvoiceRevertsWhatThePublisherWrote() {
        failReleaseOfConsumption();

        assertThatThrownBy(() -> fixtures.inOneTransaction(() -> {
            fixtures.renameInstitutionOf(student, "Written by the publisher");
            eventPublisher.publishEvent(new InvoiceCancelledEvent(invoiceId, LocalDate.now()));
        })).rootCause().hasMessageContaining("consumption release failed");

        assertThat(fixtures.institutionNameOf(student)).isEqualTo(ORIGINAL_NAME);
        assertThat(releasedApplicationsOf(invoiceId)).isZero();
    }

    @Test
    void releaseFailureOnDiscardedInvoiceRevertsWhatThePublisherWrote() {
        failReleaseOfConsumption();

        assertThatThrownBy(() -> fixtures.inOneTransaction(() -> {
            fixtures.renameInstitutionOf(student, "Written by the publisher");
            eventPublisher.publishEvent(new InvoiceDiscardedEvent(invoiceId, "discarded in test"));
        })).rootCause().hasMessageContaining("consumption release failed");

        assertThat(fixtures.institutionNameOf(student)).isEqualTo(ORIGINAL_NAME);
        assertThat(releasedApplicationsOf(invoiceId)).isZero();
    }

    @Test
    void successfulReleaseOnCancelledInvoiceCommitsTogetherWithWhatThePublisherWrote() {
        fixtures.inOneTransaction(() -> {
            fixtures.renameInstitutionOf(student, "Written by the publisher");
            eventPublisher.publishEvent(new InvoiceCancelledEvent(invoiceId, LocalDate.now()));
        });

        assertThat(fixtures.institutionNameOf(student)).isEqualTo("Written by the publisher");
        assertThat(releasedApplicationsOf(invoiceId)).isEqualTo(1);
    }

    @Test
    void successfulReleaseOnDiscardedInvoiceCommitsTogetherWithWhatThePublisherWrote() {
        fixtures.inOneTransaction(() -> {
            fixtures.renameInstitutionOf(student, "Written by the publisher");
            eventPublisher.publishEvent(new InvoiceDiscardedEvent(invoiceId, "discarded in test"));
        });

        assertThat(fixtures.institutionNameOf(student)).isEqualTo("Written by the publisher");
        assertThat(releasedApplicationsOf(invoiceId)).isEqualTo(1);
    }

    private void failReleaseOfConsumption() {
        fixtures.failOn("invoice_discount", "update", "new.consumption_released_at is not null", "consumption release failed");
    }

    private Integer releasedApplicationsOf(Long invoiceId) {
        return jdbcTemplate.queryForObject("""
                select count(*) from invoice_discount d join invoice_line l on l.id = d.invoice_line_id
                where l.invoice_id = ? and d.consumption_released_at is not null
                """, Integer.class, invoiceId);
    }
}
