package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.note.domain.CancelInvoiceCommand;
import com.red_escuela.backend.billing.note.domain.CancelInvoicesAfterWithdrawalCommand;
import com.red_escuela.backend.billing.note.domain.CreditNoteReason;
import com.red_escuela.backend.billing.note.domain.SystemCancellationOrigin;
import com.red_escuela.backend.billing.note.domain.WithdrawalCancellationItem;
import com.red_escuela.backend.billing.note.exception.PendingBankTransferOnInvoiceException;
import com.red_escuela.backend.billing.note.service.CancelInvoicesAfterWithdrawalOperation;
import com.red_escuela.backend.billing.note.service.SystemAdmissionInvoiceCancellation;
import com.red_escuela.backend.shared.time.MutableClock;
import com.red_escuela.backend.testsupport.SlowTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-291")
@SlowTest(reason = "Runs the withdrawal, system and concurrent cancellations of installments with late fees against Postgres")
class LateFeeAnnulmentOnCancellationCallersIT extends AbstractLateFeeAnnulmentOnCancellationIT {

    @Autowired
    private CancelInvoicesAfterWithdrawalOperation withdrawalOperation;
    @Autowired
    private SystemAdmissionInvoiceCancellation admissionCancellation;

    private CancelInvoicesAfterWithdrawalCommand withdrawalOf(Invoice... installments) {
        List<WithdrawalCancellationItem> items = Arrays.stream(installments).map(invoice -> WithdrawalCancellationItem.cancelling(invoice.getId())).toList();
        return new CancelInvoicesAfterWithdrawalCommand(items, d(2611, 4, 1), person.getId(), true, null);
    }

    @Test
    @Tag("C3-10")
    @Tag("D-400")
    void aWithdrawalCancellingALaterInstallmentAnnulsItsLateFeesInTheSameRequest() {
        Invoice origin = marchInstallmentWithTheMarchAndAprilCloses(2610);
        Long marchNote = debitNoteIdOf(origin, 0);
        Long aprilNote = debitNoteIdOf(origin, 1);
        CancelInvoicesAfterWithdrawalCommand command = new CancelInvoicesAfterWithdrawalCommand(List.of(WithdrawalCancellationItem.cancelling(origin.getId())), d(2610, 3, 1),
                person.getId(), true, null);

        pipeline.runOperation(withdrawalOperation, command).resultOrThrowIfAwaitingAuthorization(id -> new IllegalStateException("Unexpected authorization " + id));

        assertThat(paymentStatusOf(origin.getId())).isEqualTo("CANCELLED");
        assertThat(liveLateFeeDebitNotesOf(origin)).isZero();
        assertThat(onlyCreditNoteOf(marchNote)).containsEntry("reason", "LATE_FEE_ANNULMENT");
        assertThat(onlyCreditNoteOf(aprilNote)).containsEntry("reason", "LATE_FEE_ANNULMENT");
        assertThat(onlyCreditNoteOf(origin.getId())).containsEntry("reason", "CANCELLATION");
        assertThat(balanceOfAccount(origin)).isEqualByComparingTo("0.00");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("C3-11")
    @Tag("D-400")
    void aLateFeeUnderPaymentReviewRejectsTheWholeWithdrawalRequestLeavingTheOtherInstallmentsUntouched() {
        Invoice origin = marchInstallmentWithTheMarchAndAprilCloses(2611);
        Long aprilNote = debitNoteIdOf(origin, 1);
        Invoice other = overdueInstallmentDueOn(d(2611, 4, 11));
        jdbc.update("update invoice set payment_status = 'PAYMENT_UNDER_REVIEW' where id = ?", aprilNote);

        assertThatThrownBy(() -> pipeline.runOperation(withdrawalOperation, withdrawalOf(other, origin)).resultOrThrowIfAwaitingAuthorization(
                id -> new IllegalStateException("Unexpected authorization " + id))).isInstanceOf(PendingBankTransferOnInvoiceException.class)
                .satisfies(thrown -> assertThat(((PendingBankTransferOnInvoiceException) thrown).violations().get(0).code()).isEqualTo("LATE_FEE_UNDER_PAYMENT_REVIEW"));

        assertThat(paymentStatusOf(other.getId())).isEqualTo("OVERDUE");
        assertThat(paymentStatusOf(origin.getId())).isEqualTo("OVERDUE");
        assertThat(countOfCreditNotesOf(other.getId(), origin.getId(), aprilNote)).isZero();
    }

    @Test
    @Tag("C3-12")
    @Tag("D-400")
    void systemCancellationsAnnulTheLateFeesOfTheCancelledInvoice() {
        Invoice origin = marchInstallmentWithTheMarchAndAprilCloses(2612);
        Long marchNote = debitNoteIdOf(origin, 0);
        Long aprilNote = debitNoteIdOf(origin, 1);

        pipeline.runOperation(cancelInvoiceOperation, CancelInvoiceCommand.bySystem(origin.getId(), SystemCancellationOrigin.FUTURE_ENROLLMENT_FEE_ON_WITHDRAWAL, "Baja"))
                .resultOrThrowIfAwaitingAuthorization(id -> new IllegalStateException("Unexpected authorization " + id));

        assertThat(paymentStatusOf(origin.getId())).isEqualTo("CANCELLED");
        assertThat(liveLateFeeDebitNotesOf(origin)).isZero();
        assertThat(onlyCreditNoteOf(marchNote)).containsEntry("reason", "LATE_FEE_ANNULMENT");
        assertThat(onlyCreditNoteOf(aprilNote)).containsEntry("reason", "LATE_FEE_ANNULMENT");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("C3-13")
    @Tag("D-400")
    void theAdmissionExpiryCancellationAnnulsTheLateFeesOfTheEnrollmentFee() {
        Invoice origin = marchInstallmentWithTheMarchAndAprilCloses(2613);
        Long marchNote = debitNoteIdOf(origin, 0);
        Long aprilNote = debitNoteIdOf(origin, 1);

        SystemAdmissionInvoiceCancellation.Outcome outcome = transactionTemplate.execute(status -> admissionCancellation.cancelWhenUnpaid(origin.getId()));

        assertThat(outcome).isEqualTo(SystemAdmissionInvoiceCancellation.Outcome.CANCELLED);
        assertThat(liveLateFeeDebitNotesOf(origin)).isZero();
        assertThat(onlyCreditNoteOf(marchNote)).containsEntry("reason", "LATE_FEE_ANNULMENT");
        assertThat(onlyCreditNoteOf(aprilNote)).containsEntry("reason", "LATE_FEE_ANNULMENT");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("C3-15")
    @Tag("D-153")
    void theAdmissionExpiryLeavesTheInvoiceUntouchedWhileOneOfItsLateFeesIsUnderPaymentReview() {
        Invoice origin = marchInstallmentWithTheMarchAndAprilCloses(2615);
        Long marchNote = debitNoteIdOf(origin, 0);
        Long aprilNote = debitNoteIdOf(origin, 1);
        jdbc.update("update invoice set payment_status = 'PAYMENT_UNDER_REVIEW' where id = ?", aprilNote);

        SystemAdmissionInvoiceCancellation.Outcome outcome = transactionTemplate.execute(status -> admissionCancellation.cancelWhenUnpaid(origin.getId()));

        assertThat(outcome).isEqualTo(SystemAdmissionInvoiceCancellation.Outcome.NOT_CANCELLABLE);
        assertThat(paymentStatusOf(origin.getId())).isEqualTo("OVERDUE");
        assertThat(countOfCreditNotesOf(origin.getId(), marchNote, aprilNote)).isZero();
    }

    @Test
    @Tag("C3-14")
    void aLateFeeSweepAndTheCancellationOfItsInstallmentNeverLeaveALiveLateFeeOverACancelledInstallment() throws Exception {
        for (int round = 0; round < 3; round++) {
            int year = 2620 + round;
            arrange(year);
            publishDefaults(year);
            Invoice origin = overdueInstallmentDueOn(d(year, 3, 11));
            if (round > 0) {
                reconcile(origin, d(year, 4, 1));
            }
            MutableClock.INSTANCE.setDate(d(year, 5, 1));
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                List<Callable<Object>> tasks = List.of(() -> {
                    start.await();
                    return accrualService.reconcileLateFeesOfInvoice(origin.getId(), d(year, 5, 1));
                }, () -> {
                    start.await();
                    return cancel(origin, CreditNoteReason.BILLING_ERROR);
                });
                List<Future<Object>> futures = tasks.stream().map(executor::submit).toList();
                start.countDown();
                for (Future<Object> future : futures) {
                    future.get();
                }
            } finally {
                executor.shutdownNow();
            }
            awaitEventsDrained();

            assertThat(paymentStatusOf(origin.getId())).as("round " + round).isEqualTo("CANCELLED");
            assertThat(liveLateFeeDebitNotesOf(origin)).as("round " + round).isZero();
            assertThat(balanceOfAccount(origin)).as("round " + round).isEqualByComparingTo("0.00");
            invariants.assertAllBillingInvariantsHold();
        }
    }
}
