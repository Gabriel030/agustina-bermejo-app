package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.collections.operation.LateFeeAccrualResult;
import com.red_escuela.backend.billing.collections.operation.LateFeeAccrualStep;
import com.red_escuela.backend.billing.events.LateFeeAnnulledByPaymentDateEvent;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.LateFeeCorrectionNotificationListener;
import com.red_escuela.backend.notification.entity.Notification;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.notification.repository.NotificationRepository;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.time.MutableClock;
import com.red_escuela.backend.testsupport.SlowTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@Tag("D-378")
@SlowTest(reason = "Eleven scenarios that each seed overdue installments, annul their late fees by payment date and run the real nightly recovery and events against Postgres")
class LateFeePaymentDateRecoveryIT extends AbstractLateFeeCorrectionIT {

    @Autowired
    private LateFeeAccrualStep accrualStep;
    @Autowired
    private LateFeeSettlementCorrectionService settlementCorrectionService;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private NotificationRepository notifications;
    @Autowired
    private LateFeeCorrectionNotificationListener notificationListener;

    private Invoice installmentWithMarchClosed(int year, int graceDays) {
        arrange(year);
        publish(graceDays, "0.002", 10, true, true, d(year, 1, 1));
        Invoice origin = overdueInstallmentDueOn(d(year, 3, 11));
        reconcile(origin, d(year, 4, 2));
        return origin;
    }

    private void failTheSettlementCorrectionWhile(Runnable action) {
        LateFeeSettlementCorrectionService target = AopTestUtils.getUltimateTargetObject(settlementCorrectionService);
        Object original = ReflectionTestUtils.getField(target, "issuer");
        LateFeeCorrectionIssuer failing = org.mockito.Mockito.mock(LateFeeCorrectionIssuer.class, invocation -> {
            throw new IllegalStateException("injected failure");
        });
        ReflectionTestUtils.setField(target, "issuer", failing);
        try {
            action.run();
        } finally {
            ReflectionTestUtils.setField(target, "issuer", original);
        }
    }

    private BigDecimal creditedTotalOf(Long invoiceId) {
        return jdbc.queryForObject("select coalesce(sum(total_amount), 0) from credit_note where invoice_id = ?", BigDecimal.class, invoiceId);
    }

    private String paymentStatusOf(Long invoiceId) {
        return jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, invoiceId);
    }

    @Test
    @Tag("D-243")
    void nightlyRunAnnulsTheDaysOfASettlementWhoseListenerNeverRan() {
        Invoice origin = installmentWithMarchClosed(2501, 0);
        Long debitNoteId = debitNoteIdOf(origin, 0);
        MutableClock.INSTANCE.setDate(d(2501, 4, 2));
        BillingTestFixtures.payInvoiceInFullWithMoneyOf(origin, d(2501, 3, 28), d(2501, 4, 2));

        failTheSettlementCorrectionWhile(() -> assertThatThrownBy(() -> accrualService.reconcileLateFeesOfSettledInvoice(origin.getId(), d(2501, 4, 2)))
                .isInstanceOf(IllegalStateException.class));
        assertThat(creditNotesOf(debitNoteId)).isEmpty();
        MutableClock.INSTANCE.setDate(d(2501, 4, 3));
        LateFeeAccrualResult first = accrualStep.accrueLateFees(d(2501, 4, 3));
        MutableClock.INSTANCE.setDate(d(2501, 4, 4));
        LateFeeAccrualResult second = accrualStep.accrueLateFees(d(2501, 4, 4));
        awaitEventsDrained();

        assertThat(first.settlementCandidates()).isEqualTo(1);
        assertThat(second.settlementCandidates()).isZero();
        assertThat(creditNotesOf(debitNoteId)).hasSize(1);
        assertThat(creditedTotalOf(debitNoteId)).isEqualByComparingTo("600.00");
        assertThat(netChargedDaysOf(debitNoteId)).isEqualByComparingTo("17");
        assertScenarioInvariants(d(2501, 4, 4));
    }

    @Test
    @Tag("D-331")
    void nightlyRunAnnulsEveryDayOfAPaymentInsideTheGraceWhoseListenerNeverRan() {
        Invoice origin = installmentWithMarchClosed(2502, 10);
        Long debitNoteId = debitNoteIdOf(origin, 0);
        MutableClock.INSTANCE.setDate(d(2502, 4, 2));
        BillingTestFixtures.payInvoiceInFullWithMoneyOf(origin, d(2502, 3, 20), d(2502, 4, 2));

        failTheSettlementCorrectionWhile(() -> assertThatThrownBy(() -> accrualService.reconcileLateFeesOfSettledInvoice(origin.getId(), d(2502, 4, 2)))
                .isInstanceOf(IllegalStateException.class));
        assertThat(creditNotesOf(debitNoteId)).isEmpty();
        MutableClock.INSTANCE.setDate(d(2502, 4, 3));
        LateFeeAccrualResult first = accrualStep.accrueLateFees(d(2502, 4, 3));
        MutableClock.INSTANCE.setDate(d(2502, 4, 4));
        LateFeeAccrualResult second = accrualStep.accrueLateFees(d(2502, 4, 4));
        awaitEventsDrained();

        assertThat(first.settlementCandidates()).isEqualTo(1);
        assertThat(second.settlementCandidates()).isZero();
        assertThat(creditNotesOf(debitNoteId)).hasSize(1);
        assertThat(creditedTotalOf(debitNoteId)).isEqualByComparingTo("4000.00");
        assertThat(paymentStatusOf(debitNoteId)).isEqualTo("CANCELLED");
        assertThat(netChargedDaysOf(debitNoteId)).isZero();
        assertScenarioInvariants(d(2502, 4, 4));
    }

    @Test
    @Tag("D-331")
    void nightlyRunAnnulsADebitNoteEntirelyBeforeThePaymentDateWhenPaidInsideTheGrace() {
        int year = 2531;
        arrange(year);
        publish(15, "0.002", 10, true, true, d(year, 1, 1));
        Invoice origin = overdueInstallmentDueOn(d(year, 2, 20));
        reconcile(origin, d(year, 3, 8));
        Long debitNoteId = debitNoteIdOf(origin, 0);
        assertThat(summaryOf(origin)).containsExactly(year + "-02-21.." + year + "-02-28:8:1600.00");
        MutableClock.INSTANCE.setDate(d(year, 3, 9));
        BillingTestFixtures.payInvoiceInFullWithMoneyOf(origin, d(year, 3, 5), d(year, 3, 9));

        failTheSettlementCorrectionWhile(() -> assertThatThrownBy(() -> accrualService.reconcileLateFeesOfSettledInvoice(origin.getId(), d(year, 3, 9)))
                .isInstanceOf(IllegalStateException.class));
        assertThat(creditNotesOf(debitNoteId)).isEmpty();
        MutableClock.INSTANCE.setDate(d(year, 3, 10));
        LateFeeAccrualResult first = accrualStep.accrueLateFees(d(year, 3, 10));
        awaitEventsDrained();

        assertThat(first.settlementCandidates()).isEqualTo(1);
        assertThat(creditedTotalOf(debitNoteId)).isEqualByComparingTo("1600.00");
        assertThat(paymentStatusOf(debitNoteId)).isEqualTo("CANCELLED");
        assertThat(netChargedDaysOf(debitNoteId)).isZero();
        assertScenarioInvariants(d(year, 3, 10));
    }

    @Test
    @Tag("D-117")
    void aRedeliveredAnnulmentEventNotifiesTheStudentOnce() {
        arrange(2532);
        Long creditNoteId = System.nanoTime();
        LateFeeAnnulledByPaymentDateEvent event = LateFeeAnnulledByPaymentDateEvent.annulledFor(creditNoteId, "NC-00000099", 77L, "ND-00000077", person.getId(), 3,
                d(2532, 3, 28), false, NonNegativeMoney.of(new BigDecimal("600.00"), Currency.ARS));
        LateFeeCorrectionNotificationListener listener = AopTestUtils.getUltimateTargetObject(notificationListener);

        listener.notifyStudentOfTheAnnulmentByPaymentDate(event);
        await().atMost(Duration.ofSeconds(25)).until(() -> studentNoticesOf(creditNoteId) == 1L);
        listener.notifyStudentOfTheAnnulmentByPaymentDate(event);

        await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(10)).until(() -> studentNoticesOf(creditNoteId) == 1L);
    }

    private long studentNoticesOf(Long creditNoteId) {
        return notifications.findByRecipientIdOrderByCreatedAtDesc(person.getId()).stream()
                .filter(notification -> creditNoteId.equals(notification.getRelatedEntityId())).count();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @Tag("D-153")
    void annulmentWaitsWhileTheDebitNoteIsUnderReviewAndRetriesOnDecision(boolean approved) {
        int year = approved ? 2503 : 2504;
        Invoice origin = installmentWithMarchClosed(year, 0);
        Long debitNoteId = debitNoteIdOf(origin, 0);
        transactionTemplate.executeWithoutResult(status -> invoiceRepository.findByIdForUpdate(debitNoteId).orElseThrow()
                .markPaymentUnderReview(d(year, 4, 1), ACTOR, "Comprobante"));
        MutableClock.INSTANCE.setDate(d(year, 4, 2));

        payInFull(origin, d(year, 3, 28), d(year, 4, 2));
        awaitEventsDrained();
        MutableClock.INSTANCE.setDate(d(year, 4, 3));
        accrualStep.accrueLateFees(d(year, 4, 3));
        awaitEventsDrained();

        assertThat(creditNotesOf(debitNoteId)).isEmpty();
        assertThat(paymentStatusOf(debitNoteId)).isEqualTo("PAYMENT_UNDER_REVIEW");

        MutableClock.INSTANCE.setDate(d(year, 4, 5));
        if (approved) {
            receiveMoney(origin, "4000.00", d(year, 4, 5));
            allocateOn(origin, d(year, 4, 5));
        } else {
            transactionTemplate.executeWithoutResult(status -> {
                Invoice reviewed = invoiceRepository.findByIdForUpdate(debitNoteId).orElseThrow();
                reviewed.releasePaymentReview(d(year, 4, 5), 0, ACTOR, "Comprobante rechazado");
                reviewed.pullRecordedDomainEvents().forEach(eventPublisher::publishEvent);
            });
        }
        awaitEventsDrained();
        assertThat(creditNotesOf(debitNoteId)).as("the decision of the receipt retries the annulment before the nightly run").hasSize(1);
        MutableClock.INSTANCE.setDate(d(year, 4, 6));
        accrualStep.accrueLateFees(d(year, 4, 6));
        awaitEventsDrained();

        List<Map<String, Object>> creditNotes = creditNotesOf(debitNoteId);
        assertThat(creditNotes).hasSize(1);
        assertThat((BigDecimal) creditNotes.get(0).get("total_amount")).isEqualByComparingTo("600.00");
        if (approved) {
            assertThat(paymentStatusOf(debitNoteId)).isEqualTo("PAID");
            assertThat((BigDecimal) creditNotes.get(0).get("free_credit_amount")).isEqualByComparingTo("600.00");
            assertThat((BigDecimal) creditNotes.get(0).get("offset_amount")).isEqualByComparingTo("0.00");
        } else {
            assertThat((BigDecimal) creditNotes.get(0).get("offset_amount")).isEqualByComparingTo("600.00");
            assertThat(jdbc.queryForObject("select outstanding_amount from invoice where id = ?", BigDecimal.class, debitNoteId)).isEqualByComparingTo("3400.00");
        }
        assertThat(netChargedDaysOf(debitNoteId)).isEqualByComparingTo("17");
        assertScenarioInvariants(d(year, 4, 6));
    }

    @ParameterizedTest
    @CsvSource({"2511,13,0", "2512,15,0", "2513,16,1", "2514,20,1"})
    @Tag("D-325")
    @Tag("D-375")
    void annulmentIssuedAfterTheFiscalWindowNotifiesTheSecretariat(int year, int daysAfterTheRegistration, long expectedNotices) {
        Person secretariat = persistSecretariat();
        Invoice origin = installmentWithMarchClosed(year, 0);
        Long debitNoteId = debitNoteIdOf(origin, 0);
        BillingTestFixtures.payInvoiceInFullWithMoneyOf(origin, d(year, 3, 28), d(year, 4, 2));
        LocalDate sweepDay = d(year, 4, 2).plusDays(daysAfterTheRegistration);
        MutableClock.INSTANCE.setDate(sweepDay);

        accrualStep.accrueLateFees(sweepDay);
        awaitEventsDrained();

        List<Map<String, Object>> creditNotes = creditNotesOf(debitNoteId);
        assertThat(creditNotes).hasSize(1);
        assertThat(creditNotes.get(0)).containsEntry("fact_date", Date.valueOf(d(year, 4, 2))).containsEntry("issue_date", Date.valueOf(sweepDay));
        assertThat(fiscalWindowNoticesOf(secretariat, debitNoteId)).isEqualTo(expectedNotices);
        if (expectedNotices > 0) {
            String creditNoteNumber = (String) creditNotes.get(0).get("credit_note_number");
            String debitNoteNumber = (String) creditNotes.get(0).get("invoice_number");
            assertThat(fiscalWindowNoticeMessagesOf(secretariat, debitNoteId)).containsExactly("La nota de crédito " + creditNoteNumber
                    + " (anulación del recargo de la nota de débito " + debitNoteNumber + ") se emitió el " + formatted(sweepDay) + ", y el pago se registró el "
                    + formatted(d(year, 4, 2)) + ": fuera de los 15 días de la RG 4540. Revisala con el contador.");
        }
        assertScenarioInvariants(sweepDay);
    }

    @Test
    @Tag("D-117")
    void studentIsNotifiedOfTheDaysAnnulledByTheirPaymentDate() {
        Invoice afterPayment = installmentWithMarchClosed(2521, 0);
        MutableClock.INSTANCE.setDate(d(2521, 4, 2));
        payInFull(afterPayment, d(2521, 3, 28), d(2521, 4, 2));
        awaitEventsDrained();
        Map<String, Object> annulment = creditNotesOf(debitNoteIdOf(afterPayment, 0)).get(0);
        assertThat(studentNoticesOf(annulment)).containsExactly("Ajustamos el recargo por mora " + annulment.get("invoice_number")
                + ": no te cobramos los 3 días posteriores a tu pago del 28/03/2521 (nota de crédito " + annulment.get("credit_note_number") + " por ARS 600.00).");

        Invoice withinGrace = installmentWithMarchClosed(2522, 10);
        MutableClock.INSTANCE.setDate(d(2522, 4, 2));
        payInFull(withinGrace, d(2522, 3, 20), d(2522, 4, 2));
        awaitEventsDrained();
        Map<String, Object> graceAnnulment = creditNotesOf(debitNoteIdOf(withinGrace, 0)).get(0);
        assertThat(studentNoticesOf(graceAnnulment)).containsExactly("Ajustamos el recargo por mora " + graceAnnulment.get("invoice_number")
                + ": no te cobramos los 20 días porque pagaste el 20/03/2522, dentro del plazo de gracia (nota de crédito " + graceAnnulment.get("credit_note_number")
                + " por ARS 4000.00).");

        Invoice singleDay = installmentWithMarchClosed(2523, 0);
        MutableClock.INSTANCE.setDate(d(2523, 4, 2));
        payInFull(singleDay, d(2523, 3, 30), d(2523, 4, 2));
        awaitEventsDrained();
        Map<String, Object> singleDayAnnulment = creditNotesOf(debitNoteIdOf(singleDay, 0)).get(0);
        assertThat(studentNoticesOf(singleDayAnnulment)).containsExactly("Ajustamos el recargo por mora " + singleDayAnnulment.get("invoice_number")
                + ": no te cobramos el día posterior a tu pago del 30/03/2523 (nota de crédito " + singleDayAnnulment.get("credit_note_number") + " por ARS 200.00).");
    }

    private List<String> studentNoticesOf(Map<String, Object> creditNote) {
        Long creditNoteId = ((Number) creditNote.get("id")).longValue();
        return notifications.findByRecipientIdOrderByCreatedAtDesc(person.getId()).stream()
                .filter(notification -> notification.getType() == NotificationType.CREDIT_NOTE_ISSUED && creditNoteId.equals(notification.getRelatedEntityId()))
                .map(Notification::getMessage).toList();
    }

    private static String formatted(LocalDate day) {
        return String.format("%02d/%02d/%04d", day.getDayOfMonth(), day.getMonthValue(), day.getYear());
    }
}
