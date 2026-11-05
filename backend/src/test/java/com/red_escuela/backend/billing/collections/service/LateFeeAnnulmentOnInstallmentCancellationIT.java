package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.note.domain.CancelInvoiceCommand;
import com.red_escuela.backend.billing.note.domain.CreditNoteReason;
import com.red_escuela.backend.billing.note.exception.PendingBankTransferOnInvoiceException;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.shared.time.MutableClock;
import com.red_escuela.backend.testsupport.SlowTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

@Tag("D-291")
@SlowTest(reason = "Nine scenarios that each seed an installment with two month closes and cancel it against Postgres")
class LateFeeAnnulmentOnInstallmentCancellationIT extends AbstractLateFeeAnnulmentOnCancellationIT {

    @Test
    @Tag("C3-01")
    @Tag("D-202")
    void cancellingAnInstallmentForBillingErrorAnnulsItsLiveLateFeesInTheSameOperation() {
        Invoice origin = marchInstallmentWithTheMarchAndAprilCloses(2601);
        Long marchNote = debitNoteIdOf(origin, 0);
        Long aprilNote = debitNoteIdOf(origin, 1);

        cancel(origin, CreditNoteReason.BILLING_ERROR);

        assertThat(paymentStatusOf(origin.getId())).isEqualTo("CANCELLED");
        assertThat(paymentStatusOf(marchNote)).isEqualTo("CANCELLED");
        assertThat(paymentStatusOf(aprilNote)).isEqualTo("CANCELLED");
        Map<String, Object> marchAnnulment = onlyCreditNoteOf(marchNote);
        Map<String, Object> aprilAnnulment = onlyCreditNoteOf(aprilNote);
        assertThat(marchAnnulment).containsEntry("reason", "LATE_FEE_ANNULMENT");
        assertThat(aprilAnnulment).containsEntry("reason", "LATE_FEE_ANNULMENT");
        assertThat((BigDecimal) marchAnnulment.get("total_amount")).isEqualByComparingTo("4000.00");
        assertThat((BigDecimal) marchAnnulment.get("offset_amount")).isEqualByComparingTo("4000.00");
        assertThat((BigDecimal) aprilAnnulment.get("total_amount")).isEqualByComparingTo("6000.00");
        assertThat((BigDecimal) aprilAnnulment.get("free_credit_amount")).isEqualByComparingTo("0.00");
        assertThat(onlyCreditNoteOf(origin.getId())).containsEntry("reason", "BILLING_ERROR");
        assertThat(jdbc.queryForList("select distinct xmin::text from credit_note where invoice_id in (?, ?, ?)", String.class, origin.getId(), marchNote, aprilNote))
                .as("the three credit notes are written by one transaction").hasSize(1);
        assertThat(releasedChargeKeysOf(origin.getId())).as("the installment releases its key (D-202)").isEqualTo(1L);
        assertThat(releasedChargeKeysOf(marchNote) + releasedChargeKeysOf(aprilNote)).as("a late fee never releases its key (D-202)").isZero();
        assertThat(balanceOfAccount(origin)).isEqualByComparingTo("0.00");
        assertThat(unallocatedCreditOfAccount(origin)).isEqualByComparingTo("0.00");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("C3-06")
    @Tag("D-245")
    void cancellingAnInstallmentForCancellationAnnulsItsLiveLateFeesToo() {
        Invoice origin = marchInstallmentWithTheMarchAndAprilCloses(2602);
        Long marchNote = debitNoteIdOf(origin, 0);
        Long aprilNote = debitNoteIdOf(origin, 1);

        cancel(origin, CreditNoteReason.CANCELLATION);

        assertThat(paymentStatusOf(origin.getId())).isEqualTo("CANCELLED");
        assertThat(liveLateFeeDebitNotesOf(origin)).isZero();
        assertThat(onlyCreditNoteOf(marchNote)).containsEntry("reason", "LATE_FEE_ANNULMENT");
        assertThat(onlyCreditNoteOf(aprilNote)).containsEntry("reason", "LATE_FEE_ANNULMENT");
        assertThat(onlyCreditNoteOf(origin.getId())).containsEntry("reason", "CANCELLATION");
        assertThat(releasedChargeKeysOf(origin.getId())).isZero();
        assertThat(balanceOfAccount(origin)).isEqualByComparingTo("0.00");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("C3-02")
    void aFailedLateFeeAnnulmentRollsBackTheInstallmentCancellation() {
        Invoice origin = marchInstallmentWithTheMarchAndAprilCloses(2603);
        Long marchNote = debitNoteIdOf(origin, 0);
        Long aprilNote = debitNoteIdOf(origin, 1);
        BigDecimal balanceBefore = balanceOfAccount(origin);
        String function = "fail_credit_note_of_debit_note_" + aprilNote;
        jdbc.execute("create or replace function " + function + "() returns trigger as $$ begin raise exception 'injected failure'; end $$ language plpgsql");
        jdbc.execute("create trigger " + function + " before insert on credit_note for each row when (new.invoice_id = " + aprilNote + ") execute function " + function + "()");
        try {
            assertThatThrownBy(() -> cancel(origin, CreditNoteReason.BILLING_ERROR)).hasMessageContaining("injected failure");
        } finally {
            jdbc.execute("drop trigger if exists " + function + " on credit_note");
            jdbc.execute("drop function if exists " + function + "()");
        }

        assertNothingWasCancelled(origin, marchNote, aprilNote);
        assertThat(releasedChargeKeysOf(origin.getId())).isZero();
        assertThat(balanceOfAccount(origin)).isEqualByComparingTo(balanceBefore);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("C3-03")
    @Tag("D-202")
    void annulledLateFeeIsNeverReissued() {
        Invoice origin = marchInstallmentWithTheMarchAndAprilCloses(2604);
        Long marchNote = debitNoteIdOf(origin, 0);
        Long aprilNote = debitNoteIdOf(origin, 1);
        cancel(origin, CreditNoteReason.BILLING_ERROR);
        long debitNotesBefore = jdbc.queryForObject("select count(*) from invoice where document_type = 'DEBIT_NOTE' and student_current_account_id = ?", Long.class,
                origin.getStudentCurrentAccountId());

        MutableClock.INSTANCE.setDate(d(2604, 6, 1));
        int issued = accrualService.reconcileLateFeesOfInvoice(origin.getId(), d(2604, 6, 1));
        awaitEventsDrained();

        assertThat(issued).isZero();
        assertThat(summaryOf(origin)).hasSize(2);
        assertThat(jdbc.queryForObject("select count(*) from invoice where document_type = 'DEBIT_NOTE' and student_current_account_id = ?", Long.class,
                origin.getStudentCurrentAccountId())).isEqualTo(debitNotesBefore);
        assertThat(paymentStatusOf(marchNote)).isEqualTo("CANCELLED");
        assertThat(paymentStatusOf(aprilNote)).isEqualTo("CANCELLED");
        assertThat(releasedChargeKeysOf(marchNote) + releasedChargeKeysOf(aprilNote)).isZero();
        assertScenarioInvariants(d(2604, 6, 1));
    }

    @Test
    @Tag("C3-04")
    @Tag("D-159")
    void paidLateFeeAnnulledByCancellationLeavesTheCreditInFavour() {
        Invoice origin = marchInstallmentWithTheMarchAndAprilCloses(2605);
        Long marchNote = debitNoteIdOf(origin, 0);
        Long aprilNote = debitNoteIdOf(origin, 1);
        receiveMoney(origin, "4000.00", d(2605, 5, 2));
        transactionTemplate.executeWithoutResult(status -> allocationService.allocateAccountCreditToOldestInvoicesSkipping(
                accountService.lockStudentCurrentAccount(origin.getStudentCurrentAccountId()), d(2605, 5, 2), ACTOR, Set.of(origin.getId())));
        assertThat(paymentStatusOf(marchNote)).isEqualTo("PAID");

        cancel(origin, CreditNoteReason.BILLING_ERROR);

        assertThat(paymentStatusOf(origin.getId())).isEqualTo("CANCELLED");
        assertThat(paymentStatusOf(marchNote)).isEqualTo("PAID");
        assertThat(paymentStatusOf(aprilNote)).isEqualTo("CANCELLED");
        Map<String, Object> paidAnnulment = onlyCreditNoteOf(marchNote);
        assertThat(paidAnnulment).containsEntry("reason", "LATE_FEE_ANNULMENT");
        assertThat((BigDecimal) paidAnnulment.get("free_credit_amount")).isEqualByComparingTo("4000.00");
        assertThat((BigDecimal) paidAnnulment.get("offset_amount")).isEqualByComparingTo("0.00");
        assertThat(unallocatedCreditOfAccount(origin)).isEqualByComparingTo("4000.00");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("C3-02")
    void aFailureOfTheInstallmentCreditNoteAfterTheLateFeesWereAnnulledRollsBackTheAnnulments() {
        Invoice origin = marchInstallmentWithTheMarchAndAprilCloses(2614);
        Long marchNote = debitNoteIdOf(origin, 0);
        Long aprilNote = debitNoteIdOf(origin, 1);
        String function = "fail_credit_note_of_installment_" + origin.getId();
        jdbc.execute("create or replace function " + function + "() returns trigger as $$ begin raise exception 'injected installment failure'; end $$ language plpgsql");
        jdbc.execute("create trigger " + function + " before insert on credit_note for each row when (new.invoice_id = " + origin.getId() + ") execute function " + function + "()");
        try {
            assertThatThrownBy(() -> cancel(origin, CreditNoteReason.BILLING_ERROR)).hasMessageContaining("injected installment failure");
        } finally {
            jdbc.execute("drop trigger if exists " + function + " on credit_note");
            jdbc.execute("drop function if exists " + function + "()");
        }

        assertNothingWasCancelled(origin, marchNote, aprilNote);
        invariants.assertAllBillingInvariantsHold();
    }

    private void assertRejectedBecauseTheLateFeeIsUnderReview(Throwable thrown, Long debitNoteId) {
        assertThat(thrown).isInstanceOf(PendingBankTransferOnInvoiceException.class);
        List<PolicyViolation> violations = ((PendingBankTransferOnInvoiceException) thrown).violations();
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).code()).isEqualTo("LATE_FEE_UNDER_PAYMENT_REVIEW");
        assertThat(violations.get(0).message()).contains("id " + debitNoteId);
    }

    @Test
    @Tag("C3-07")
    @Tag("D-153")
    void aLateFeeUnderPaymentReviewRejectsTheWholeCancellationAndNothingChanges() {
        Invoice origin = marchInstallmentWithTheMarchAndAprilCloses(2606);
        Long marchNote = debitNoteIdOf(origin, 0);
        Long aprilNote = debitNoteIdOf(origin, 1);
        jdbc.update("update invoice set payment_status = 'PAYMENT_UNDER_REVIEW' where id = ?", aprilNote);

        assertRejectedBecauseTheLateFeeIsUnderReview(catchThrowable(() -> cancel(origin, CreditNoteReason.BILLING_ERROR)), aprilNote);

        assertThat(paymentStatusOf(origin.getId())).isEqualTo("OVERDUE");
        assertThat(paymentStatusOf(marchNote)).isNotEqualTo("CANCELLED");
        assertThat(countOfCreditNotesOf(origin.getId(), marchNote, aprilNote)).isZero();
    }

    @Test
    @Tag("C3-08")
    @Tag("D-153")
    void aLateFeeFoundUnderPaymentReviewAfterTheLocksStillRollsBackEverythingBeforeAnyCreditNote() {
        Invoice origin = marchInstallmentWithTheMarchAndAprilCloses(2607);
        Long marchNote = debitNoteIdOf(origin, 0);
        Long aprilNote = debitNoteIdOf(origin, 1);
        jdbc.update("update invoice set payment_status = 'PAYMENT_UNDER_REVIEW' where id = ?", aprilNote);
        CancelInvoiceCommand command = CancelInvoiceCommand.byPerson(origin.getId(), CreditNoteReason.BILLING_ERROR, "Cuota con recargos", person.getId(), true);

        assertRejectedBecauseTheLateFeeIsUnderReview(catchThrowable(() -> transactionTemplate.execute(status -> cancelInvoiceOperation.cancelWithoutAllocatingCredit(command))),
                aprilNote);

        assertThat(paymentStatusOf(origin.getId())).isEqualTo("OVERDUE");
        assertThat(countOfCreditNotesOf(origin.getId(), marchNote, aprilNote)).isZero();
    }

    @Test
    @Tag("C3-09")
    @Tag("D-375")
    void aLateFeeAlreadyCorrectedInPartIsAnnulledOnlyForItsRemainingBalance() {
        Invoice origin = aprilDebitNoteOrigin(2608);
        Long debitNoteId = debitNoteIdOf(origin, 0);
        endEnrollmentSilently("WITHDRAWN", d(2608, 4, 28), d(2608, 5, 3));
        MutableClock.INSTANCE.setDate(d(2608, 5, 4));
        correctionService.correctLateFeesOfEnrollment(null, enrollment.getId(), d(2608, 5, 3));
        awaitEventsDrained();
        assertThat(creditNotesOf(debitNoteId)).hasSize(1);
        assertThat(paymentStatusOf(debitNoteId)).isNotEqualTo("CANCELLED");

        cancel(origin, CreditNoteReason.CANCELLATION);

        List<Map<String, Object>> notes = creditNotesOf(debitNoteId);
        assertThat(notes).hasSize(2);
        assertThat(notes.get(0)).containsEntry("reason", "LATE_FEE_CORRECTION");
        assertThat((BigDecimal) notes.get(0).get("total_amount")).isEqualByComparingTo("400.00");
        assertThat(notes.get(1)).containsEntry("reason", "LATE_FEE_ANNULMENT");
        assertThat((BigDecimal) notes.get(1).get("total_amount")).isEqualByComparingTo("3400.00");
        assertThat(paymentStatusOf(debitNoteId)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("select sum(total_amount) from credit_note where invoice_id = ?", BigDecimal.class, debitNoteId)).isEqualByComparingTo("3800.00");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("C3-05")
    @Tag("D-330")
    void reissuedInstallmentAccruesFromItsOwnDueDate() {
        Invoice origin = marchInstallmentWithTheMarchAndAprilCloses(2609);
        cancel(origin, CreditNoteReason.BILLING_ERROR);
        Invoice reissued = BillingTestFixtures.invoice().forCourseEnrollment(enrollment).forPeriod(YearMonth.of(2609, 3)).dueOn(d(2609, 5, 20)).withBaseAmount(ars(100000)).overdue().build();
        assertThat(chargesOf(reissued)).isEmpty();

        MutableClock.INSTANCE.setDate(d(2609, 6, 1));
        reconcile(reissued, d(2609, 6, 1));
        awaitEventsDrained();
        assertThat(summaryOf(reissued)).containsExactly("2609-05-21..2609-05-31:11:2200.00");

        MutableClock.INSTANCE.setDate(d(2609, 7, 1));
        reconcile(reissued, d(2609, 7, 1));
        awaitEventsDrained();
        assertThat(summaryOf(reissued)).containsExactly("2609-05-21..2609-05-31:11:2200.00", "2609-06-01..2609-06-30:30:6000.00");
        assertThat(summaryOf(origin)).hasSize(2);
        assertThat(liveLateFeeDebitNotesOf(origin)).isZero();
        assertScenarioInvariants(d(2609, 7, 1));
    }
}
