package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.collections.operation.LateFeeCorrectionSweepResult;
import com.red_escuela.backend.billing.events.EnrollmentWithdrawnForBillingEvent;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.enrollment.service.CourseEnrollmentService;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.enrollment.service.EnrollmentWithdrawal;
import com.red_escuela.backend.shared.time.MutableClock;
import com.red_escuela.backend.testsupport.SlowTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-375")
@SlowTest(reason = "Nine scenarios of the withdrawal correction backup that each seed enrollments and invoices and run the real events and reconciliation against Postgres")
class LateFeeCorrectionBackupIT extends AbstractLateFeeCorrectionIT {

    @Autowired
    private CourseEnrollmentService courseEnrollmentService;
    @Autowired
    private LateFeeCorrectionOnWithdrawalListener withdrawalListener;
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Test
    @Tag("D-293")
    void theNightlySweepCorrectsWhatTheListenerNeverDidAndAListenerRepublishedAfterwardsDoesNotDuplicate() {
        Invoice origin = aprilDebitNoteOrigin(2150);
        Long debitNoteId = debitNoteIdOf(origin, 0);
        endEnrollmentSilently("WITHDRAWN", d(2150, 4, 28), d(2150, 5, 3));
        MutableClock.INSTANCE.setDate(d(2150, 5, 4));
        assertThat(creditNotesOf(debitNoteId)).isEmpty();
        assertThat(netChargedDaysOf(debitNoteId)).isEqualByComparingTo("19");

        LateFeeCorrectionSweepResult swept = correctionStep.correctLateFeesChargedAfterTheEnrollmentEnd(d(2150, 5, 4));
        awaitEventsDrained();

        assertThat(swept.corrected()).isGreaterThanOrEqualTo(1);
        assertThat(swept.failed()).isZero();
        List<Map<String, Object>> creditNotes = creditNotesOf(debitNoteId);
        assertThat(creditNotes).hasSize(1);
        assertThat(creditNotes.get(0)).containsEntry("reason", "LATE_FEE_CORRECTION").containsEntry("fact_date", Date.valueOf(d(2150, 5, 3)))
                .containsEntry("issue_date", Date.valueOf(d(2150, 5, 4)));
        assertThat((BigDecimal) creditNotes.get(0).get("total_amount")).isEqualByComparingTo("400.00");
        assertThat(netChargedDaysOf(debitNoteId)).isEqualByComparingTo("17");

        withdrawalListener.correctLateFeesOfWithdrawnEnrollment(EnrollmentWithdrawnForBillingEvent.withdrawnFor(student.getId(), null, enrollment.getId(),
                d(2150, 4, 28), d(2150, 5, 3), "VOLUNTARY"));
        correctionStep.correctLateFeesChargedAfterTheEnrollmentEnd(d(2150, 5, 5));
        awaitEventsDrained();

        assertThat(creditNotesOf(debitNoteId)).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from late_fee_charge_correction where debit_note_id = ?", Long.class, debitNoteId)).isEqualTo(1L);
        invariants.assertAllBillingInvariantsHold();
    }

    @ParameterizedTest
    @CsvSource({"APPROVED,2161", "FAILED,2162", "WITHDRAWN,2163"})
    @Tag("D-293")
    void anEndOfTheEnrollmentOtherThanTheVoluntaryWithdrawalIsCorrectedTheSameWay(String status, int year) {
        Invoice origin = aprilDebitNoteOrigin(year);
        Long debitNoteId = debitNoteIdOf(origin, 0);
        endEnrollmentSilently(status, d(year, 4, 28), d(year, 5, 3));
        MutableClock.INSTANCE.setDate(d(year, 5, 4));

        correctionStep.correctLateFeesChargedAfterTheEnrollmentEnd(d(year, 5, 4));
        awaitEventsDrained();

        List<Map<String, Object>> creditNotes = creditNotesOf(debitNoteId);
        assertThat(creditNotes).hasSize(1);
        assertThat((BigDecimal) creditNotes.get(0).get("total_amount")).isEqualByComparingTo("400.00");
        assertThat(netChargedDaysOf(debitNoteId)).isEqualByComparingTo("17");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-153")
    void aDebitNoteUnderPaymentReviewWaitsAndTheApprovedReceiptCorrectsItAsFreeCredit() {
        Invoice origin = aprilDebitNoteOrigin(2170);
        Long debitNoteId = debitNoteIdOf(origin, 0);
        transactionTemplate.executeWithoutResult(status -> invoiceRepository.findByIdForUpdate(debitNoteId).orElseThrow()
                .markPaymentUnderReview(d(2170, 5, 2), ACTOR, "Comprobante"));
        MutableClock.INSTANCE.setDate(d(2170, 5, 3));
        courseEnrollmentService.withdraw(enrollment.getId(), EnrollmentWithdrawal.voluntaryRegisteredBy(d(2170, 4, 28), person.getId()));
        awaitEventsDrained();
        correctionStep.correctLateFeesChargedAfterTheEnrollmentEnd(d(2170, 5, 4));
        awaitEventsDrained();
        assertThat(creditNotesOf(debitNoteId)).isEmpty();

        MutableClock.INSTANCE.setDate(d(2170, 5, 6));
        receiveMoney(origin, "103800.00", d(2170, 5, 6));
        allocateOn(origin, d(2170, 5, 6));
        awaitEventsDrained();

        assertThat(jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, debitNoteId)).isEqualTo("PAID");
        List<Map<String, Object>> creditNotes = creditNotesOf(debitNoteId);
        assertThat(creditNotes).hasSize(1);
        assertThat((BigDecimal) creditNotes.get(0).get("free_credit_amount")).isEqualByComparingTo("400.00");
        assertThat((BigDecimal) creditNotes.get(0).get("offset_amount")).isEqualByComparingTo("0.00");
        assertThat(creditNotes.get(0)).containsEntry("fact_date", Date.valueOf(d(2170, 5, 3)));
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-153")
    void aDebitNoteUnderPaymentReviewWhoseReceiptIsRejectedIsCorrectedAsAReductionAndTheOtherNotesDidNotWait() {
        Invoice origin = aprilDebitNoteOrigin(2171);
        Long debitNoteId = debitNoteIdOf(origin, 0);
        transactionTemplate.executeWithoutResult(status -> invoiceRepository.findByIdForUpdate(debitNoteId).orElseThrow()
                .markPaymentUnderReview(d(2171, 5, 2), ACTOR, "Comprobante"));
        MutableClock.INSTANCE.setDate(d(2171, 5, 3));
        courseEnrollmentService.withdraw(enrollment.getId(), EnrollmentWithdrawal.voluntaryRegisteredBy(d(2171, 4, 28), person.getId()));
        awaitEventsDrained();
        assertThat(creditNotesOf(debitNoteId)).isEmpty();

        MutableClock.INSTANCE.setDate(d(2171, 5, 7));
        transactionTemplate.executeWithoutResult(status -> {
            Invoice reviewed = invoiceRepository.findByIdForUpdate(debitNoteId).orElseThrow();
            reviewed.releasePaymentReview(d(2171, 5, 7), 0, ACTOR, "Comprobante rechazado");
            reviewed.pullRecordedDomainEvents().forEach(eventPublisher::publishEvent);
        });
        awaitEventsDrained();

        List<Map<String, Object>> creditNotes = creditNotesOf(debitNoteId);
        assertThat(creditNotes).hasSize(1);
        assertThat((BigDecimal) creditNotes.get(0).get("offset_amount")).isEqualByComparingTo("400.00");
        assertThat((BigDecimal) creditNotes.get(0).get("free_credit_amount")).isEqualByComparingTo("0.00");
        assertThat(jdbc.queryForObject("select outstanding_amount from invoice where id = ?", BigDecimal.class, debitNoteId)).isEqualByComparingTo("3400.00");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-153")
    void ifNobodyDecidesTheReceiptTheSweepNeverTouchesTheDebitNote() {
        Invoice origin = aprilDebitNoteOrigin(2172);
        Long debitNoteId = debitNoteIdOf(origin, 0);
        transactionTemplate.executeWithoutResult(status -> invoiceRepository.findByIdForUpdate(debitNoteId).orElseThrow()
                .markPaymentUnderReview(d(2172, 5, 2), ACTOR, "Comprobante"));
        endEnrollmentSilently("WITHDRAWN", d(2172, 4, 28), d(2172, 5, 3));

        correctionStep.correctLateFeesChargedAfterTheEnrollmentEnd(d(2172, 5, 4));
        correctionStep.correctLateFeesChargedAfterTheEnrollmentEnd(d(2172, 5, 5));

        assertThat(creditNotesOf(debitNoteId)).isEmpty();
        assertThat(jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, debitNoteId)).isEqualTo("PAYMENT_UNDER_REVIEW");
    }

    @Test
    @Tag("D-325")
    void aCorrectionIssuedMoreThanFifteenDaysAfterTheRegistrationIsStillIssuedAndTheSecretariatIsNotified() {
        Person secretariat = persistSecretariat();
        Invoice origin = aprilDebitNoteOrigin(2180);
        Long debitNoteId = debitNoteIdOf(origin, 0);
        endEnrollmentSilently("WITHDRAWN", d(2180, 4, 28), d(2180, 5, 1));
        MutableClock.INSTANCE.setDate(d(2180, 5, 17));

        correctionStep.correctLateFeesChargedAfterTheEnrollmentEnd(d(2180, 5, 17));
        awaitEventsDrained();

        List<Map<String, Object>> creditNotes = creditNotesOf(debitNoteId);
        assertThat(creditNotes).hasSize(1);
        assertThat(creditNotes.get(0)).containsEntry("fact_date", Date.valueOf(d(2180, 5, 1))).containsEntry("issue_date", Date.valueOf(d(2180, 5, 17)));
        assertThat(fiscalWindowNoticesOf(secretariat, debitNoteId)).isEqualTo(1L);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-325")
    void aCorrectionIssuedOnTheFifteenthDayAfterTheRegistrationDoesNotNotifyTheSecretariat() {
        Person secretariat = persistSecretariat();
        Invoice origin = aprilDebitNoteOrigin(2181);
        Long debitNoteId = debitNoteIdOf(origin, 0);
        endEnrollmentSilently("WITHDRAWN", d(2181, 4, 28), d(2181, 5, 1));
        MutableClock.INSTANCE.setDate(d(2181, 5, 16));

        correctionStep.correctLateFeesChargedAfterTheEnrollmentEnd(d(2181, 5, 16));
        awaitEventsDrained();

        assertThat(creditNotesOf(debitNoteId)).hasSize(1);
        assertThat(fiscalWindowNoticesOf(secretariat, debitNoteId)).isZero();
    }

    @Test
    @Tag("D-375")
    void aDebitNoteWhoseLineHasNoCreditableBalanceIsNotACandidateOfTheSweep() {
        Invoice origin = aprilDebitNoteOrigin(2182);
        Long debitNoteId = debitNoteIdOf(origin, 0);
        Long chargeId = (Long) chargesOf(origin).get(0).get("id");
        endEnrollmentSilently("WITHDRAWN", d(2182, 4, 28), d(2182, 5, 3));
        assertThat(correctionService.findChargeIdsWithUncorrectedDaysAfter(0L, 100_000)).contains(chargeId);
        jdbc.update("update invoice_line set free_credited_amount = amount where invoice_id = ?", debitNoteId);

        assertThat(correctionService.findChargeIdsWithUncorrectedDaysAfter(0L, 100_000)).doesNotContain(chargeId);
    }

    @Test
    @Tag("D-375")
    void aDebitNoteWhoseLineBalanceWasExhaustedByAReductionIsNotACandidateOfTheSweep() {
        Invoice origin = aprilDebitNoteOrigin(2183);
        Long debitNoteId = debitNoteIdOf(origin, 0);
        Long chargeId = (Long) chargesOf(origin).get(0).get("id");
        endEnrollmentSilently("WITHDRAWN", d(2183, 4, 28), d(2183, 5, 3));
        jdbc.update("update invoice_line set credited_amount = amount where invoice_id = ?", debitNoteId);

        assertThat(correctionService.findChargeIdsWithUncorrectedDaysAfter(0L, 100_000)).doesNotContain(chargeId);
    }

    @Test
    @Tag("D-375")
    void theSweepCorrectsADebitNoteThatAlreadyHasAPartialCreditAndStillHasBalance() {
        Invoice origin = aprilDebitNoteOrigin(2184);
        Long debitNoteId = debitNoteIdOf(origin, 0);
        Long chargeId = (Long) chargesOf(origin).get(0).get("id");
        jdbc.update("update invoice_line set credited_amount = 1000.00 where invoice_id = ?", debitNoteId);
        endEnrollmentSilently("WITHDRAWN", d(2184, 4, 28), d(2184, 5, 3));
        MutableClock.INSTANCE.setDate(d(2184, 5, 4));
        assertThat(correctionService.findChargeIdsWithUncorrectedDaysAfter(0L, 100_000)).contains(chargeId);

        correctionStep.correctLateFeesChargedAfterTheEnrollmentEnd(d(2184, 5, 4));
        awaitEventsDrained();

        List<Map<String, Object>> creditNotes = creditNotesOf(debitNoteId);
        assertThat(creditNotes).hasSize(1);
        assertThat((BigDecimal) creditNotes.get(0).get("total_amount")).isEqualByComparingTo("400.00");
        assertThat(netChargedDaysOf(debitNoteId)).isEqualByComparingTo("17");
    }
}
