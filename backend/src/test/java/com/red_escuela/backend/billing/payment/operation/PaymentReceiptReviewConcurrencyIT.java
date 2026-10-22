package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.billing.payment.domain.BankTransferReceiptSubmissionResult;
import com.red_escuela.backend.billing.payment.exception.PaymentReceiptAlreadyReviewedException;
import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ErrorCodeResponse;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentReceiptReviewConcurrencyIT extends AbstractPendingTransferBridgeIT {

    private long sessionsWaitingOnALock() {
        return jdbc.queryForObject("select count(*) from pg_stat_activity where datname = current_database() and wait_event_type = 'Lock'", Long.class);
    }

    private long creditEntriesOf(Debtor debtor) {
        return jdbc.queryForObject("select count(*) from current_account_entry where student_current_account_id = ? and entry_type = 'PAYMENT_RECEIVED'", Long.class,
                debtor.accountId());
    }

    private Throwable failureOf(Future<?> review) throws InterruptedException {
        try {
            review.get(60, TimeUnit.SECONDS);
            return null;
        } catch (ExecutionException failure) {
            return failure.getCause();
        } catch (java.util.concurrent.TimeoutException timeout) {
            throw new AssertionError("The review did not finish", timeout);
        }
    }

    private record Race(Throwable firstFailure, Throwable secondFailure) {
    }

    private Race raceWhileTheAccountIsHeld(Debtor debtor, Callable<?> first, Callable<?> second) throws Exception {
        CountDownLatch accountLocked = new CountDownLatch(1);
        CountDownLatch releaseAccount = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            Future<?> holder = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                service.lockStudentCurrentAccount(debtor.accountId());
                accountLocked.countDown();
                try {
                    releaseAccount.await(60, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertThat(accountLocked.await(30, TimeUnit.SECONDS)).isTrue();
            long waitingBefore = sessionsWaitingOnALock();
            Future<?> firstReview = executor.submit(first);
            Awaitility.await().atMost(Duration.ofSeconds(30)).until(() -> sessionsWaitingOnALock() > waitingBefore);
            Future<?> secondReview = executor.submit(second);
            Awaitility.await().atMost(Duration.ofSeconds(30)).until(() -> sessionsWaitingOnALock() > waitingBefore + 1);
            releaseAccount.countDown();
            holder.get(60, TimeUnit.SECONDS);
            return new Race(failureOf(firstReview), failureOf(secondReview));
        } finally {
            releaseAccount.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @Tag("D-082")
    @Tag("D-139")
    void twoApprovalsOfTheSameReceiptOverlappingLeaveOneWinnerOneCodedConflictAndASingleCredit() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        BankTransferReceiptSubmissionResult submitted = submitOn(debtor, List.of(march), "80000.00", "OP-B20-APPROVE-APPROVE");

        Race race = raceWhileTheAccountIsHeld(debtor, () -> approve(submitted), () -> approve(submitted));

        assertThat(race.firstFailure()).isNull();
        assertThat(race.secondFailure()).isInstanceOf(PaymentReceiptAlreadyReviewedException.class);
        assertThat(ErrorCodeResponse.of(race.secondFailure()).code()).isEqualTo("PAYMENT_RECEIPT_ALREADY_REVIEWED");
        assertThat(race.secondFailure()).isInstanceOf(ConflictException.class);
        assertThat(creditEntriesOf(debtor)).isEqualTo(1L);
        assertThat(receiptStatus(submitted.receiptId())).isEqualTo("APPROVED");
        assertThat(paymentRow(submitted.paymentId())).containsEntry("status", "APPROVED");
        assertThat(paymentStatusOfInvoice(march)).isEqualTo("PAID");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-082")
    void anApprovalOverlappingARejectionLeavesTheApprovalAsWinnerAndTheRejectionAsCodedConflict() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        BankTransferReceiptSubmissionResult submitted = submitOn(debtor, List.of(march), "80000.00", "OP-B20-APPROVE-REJECT");

        Race race = raceWhileTheAccountIsHeld(debtor, () -> approve(submitted), () -> reject(submitted, "Rechazo en carrera"));

        assertThat(race.firstFailure()).isNull();
        assertThat(race.secondFailure()).isInstanceOf(PaymentReceiptAlreadyReviewedException.class);
        assertThat(creditEntriesOf(debtor)).isEqualTo(1L);
        assertThat(receiptStatus(submitted.receiptId())).isEqualTo("APPROVED");
        assertThat(paymentRow(submitted.paymentId())).containsEntry("status", "APPROVED");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-082")
    void aRejectionOverlappingAnApprovalLeavesTheRejectionAsWinnerAndNoCredit() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        BankTransferReceiptSubmissionResult submitted = submitOn(debtor, List.of(march), "80000.00", "OP-B20-REJECT-APPROVE");

        Race race = raceWhileTheAccountIsHeld(debtor, () -> reject(submitted, "Rechazo en carrera"), () -> approve(submitted));

        assertThat(race.firstFailure()).isNull();
        assertThat(race.secondFailure()).isInstanceOf(PaymentReceiptAlreadyReviewedException.class);
        assertThat(creditEntriesOf(debtor)).isZero();
        assertThat(receiptStatus(submitted.receiptId())).isEqualTo("REJECTED");
        assertThat(paymentRow(submitted.paymentId())).containsEntry("status", "REJECTED");
        assertThat(paymentStatusOfInvoice(march)).isEqualTo("PENDING");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-082")
    void simultaneousApprovalsOfManyReceiptsAlwaysEndWithOneCreditAndACodedConflictNeverAServerError() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        for (int round = 1; round <= 3; round++) {
            Debtor debtor = debtor();
            Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
            BankTransferReceiptSubmissionResult submitted = submitOn(debtor, List.of(march), "80000.00", "OP-B20-FREE-" + round);
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            List<Future<?>> approvals = new ArrayList<>();
            try {
                for (int attempt = 0; attempt < 2; attempt++) {
                    approvals.add(executor.submit(() -> {
                        start.await(30, TimeUnit.SECONDS);
                        return approve(submitted);
                    }));
                }
                start.countDown();
                int winners = 0;
                for (Future<?> approval : approvals) {
                    Throwable failure = failureOf(approval);
                    if (failure == null) {
                        winners++;
                    } else {
                        assertThat(failure).isInstanceOf(ConflictException.class);
                        assertThat(ErrorCodeResponse.of(failure).code()).isIn("PAYMENT_RECEIPT_ALREADY_REVIEWED", "INVALID_PAYMENT_RECEIPT_STATE");
                    }
                }
                assertThat(winners).isEqualTo(1);
            } finally {
                executor.shutdownNow();
            }
            assertThat(creditEntriesOf(debtor)).isEqualTo(1L);
            assertThat(paymentStatusOfInvoice(march)).isEqualTo("PAID");
        }
        invariants.assertAllBillingInvariantsHold();
    }
}
