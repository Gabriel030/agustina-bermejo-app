package com.red_escuela.backend.billing.payment.service;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.event.InvoicePaidDomainEvent;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.billing.support.ListenerAtomicityFixtures;
import com.red_escuela.backend.config.AbstractIntegrationTest;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class InvoicePaidFollowUpAtomicityIT extends AbstractIntegrationTest {

    private static final String ORIGINAL_NAME = "Original institution";
    private static final String FULL_PAYMENT_CAUSE = "FULL_PAYMENT_ALLOCATED";

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
    private Long accountId;

    @BeforeEach
    void seedAPaidAdmissionInvoice() {
        fixtures = new ListenerAtomicityFixtures(entityManager, jdbcTemplate, transactionManager);
        student = fixtures.seedStudent();
        Invoice invoice = BillingTestFixtures.invoice().forStudent(student).ofType(FeeType.ADMINISTRATIVE_FEE).paid().build();
        invoiceId = invoice.getId();
        accountId = invoice.getStudentCurrentAccountId();
        jdbcTemplate.update("update invoice set issuing_reason = 'COURSE_ADMISSION' where id = ?", invoiceId);
        fixtures.inOneTransaction(() -> fixtures.renameInstitutionOf(student, ORIGINAL_NAME));
    }

    @AfterEach
    void removeAdmissionEventSabotage() {
        fixtures.removeFailure();
    }

    @Test
    void failureWhilePublishingTheAdmissionPaidEventRevertsWhatThePublisherWrote() {
        fixtures.failOn("event_publication", "insert", "new.event_type like '%AdmissionInvoicePaidEvent'", "admission event registration failed");

        assertThatThrownBy(() -> fixtures.inOneTransaction(() -> {
            fixtures.renameInstitutionOf(student, "Written by the publisher");
            eventPublisher.publishEvent(InvoicePaidDomainEvent.paidFor(invoiceId, accountId, FULL_PAYMENT_CAUSE, LocalDate.now()));
        })).rootCause().hasMessageContaining("admission event registration failed");

        assertThat(fixtures.institutionNameOf(student)).isEqualTo(ORIGINAL_NAME);
    }

    @Test
    void successfulFollowUpCommitsTogetherWithWhatThePublisherWrote() {
        fixtures.inOneTransaction(() -> {
            fixtures.renameInstitutionOf(student, "Written by the publisher");
            eventPublisher.publishEvent(InvoicePaidDomainEvent.paidFor(invoiceId, accountId, FULL_PAYMENT_CAUSE, LocalDate.now()));
        });

        assertThat(fixtures.institutionNameOf(student)).isEqualTo("Written by the publisher");
    }
}
