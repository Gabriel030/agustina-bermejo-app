package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.billing.payment.exception.InvoiceAlreadyUnderReviewException;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class BankTransferReceiptConcurrencyIT extends AbstractBankTransferReceiptIT {

    @Test
    @Tag("D-260")
    void twoReceiptsForTheSameInvoiceAtOnceProtectItOnlyOnce() throws Exception {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<?>> uploads = new ArrayList<>();
        try {
            for (String operationNumber : List.of("OP-CONC-1", "OP-CONC-2")) {
                SubmitBankTransferReceiptCommand command = command(debtor, List.of(march), "80000.00", TODAY, operationNumber);
                uploads.add(executor.submit(() -> {
                    start.await(30, TimeUnit.SECONDS);
                    return submit(command);
                }));
            }
            start.countDown();
            int accepted = 0;
            int rejected = 0;
            for (Future<?> upload : uploads) {
                try {
                    upload.get(60, TimeUnit.SECONDS);
                    accepted++;
                } catch (ExecutionException failure) {
                    assertThat(failure.getCause()).isInstanceOf(InvoiceAlreadyUnderReviewException.class);
                    rejected++;
                }
            }
            assertThat(accepted).isEqualTo(1);
            assertThat(rejected).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }

        assertThat(jdbc.queryForObject("select count(*) from payment_checkout_item where invoice_id = ? and marked_under_review", Long.class, march)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from payment where student_current_account_id = ?", Long.class, debtor.accountId())).isEqualTo(1L);
        assertThat(paymentStatusOfInvoice(march)).isEqualTo("PAYMENT_UNDER_REVIEW");
        assertThat(storedFiles.get()).isEqualTo(1);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-260")
    void uploadWaitsForTheAccountLockBeforeItTouchesAnyInvoice() throws Exception {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        CountDownLatch accountLocked = new CountDownLatch(1);
        CountDownLatch releaseAccount = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
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
            Future<?> upload = executor.submit(() -> submit(command(debtor, List.of(march), "80000.00", TODAY, "OP-ACCOUNT-LOCK")));

            org.awaitility.Awaitility.await().atMost(20, TimeUnit.SECONDS).until(() -> jdbc.queryForObject(
                    "select count(*) from pg_stat_activity where wait_event_type = 'Lock' and datname = current_database()", Long.class) > 0);
            assertThat(upload.isDone()).isFalse();
            assertThat(paymentStatusOfInvoice(march)).isEqualTo("PENDING");
            releaseAccount.countDown();

            holder.get(30, TimeUnit.SECONDS);
            upload.get(60, TimeUnit.SECONDS);
        } finally {
            releaseAccount.countDown();
            executor.shutdownNow();
        }

        assertThat(paymentStatusOfInvoice(march)).isEqualTo("PAYMENT_UNDER_REVIEW");
    }

    @Test
    @Tag("D-154")
    @Tag("D-153")
    void uploadAtMidnightVersusOverdueSweepStaysCoherent() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2027, 3, 10, 23, 59, 59));
        int protectedWins = 0;
        int sweepWins = 0;
        for (int round = 0; round < 8; round++) {
            Debtor debtor = debtor();
            Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
            SubmitBankTransferReceiptCommand command = command(debtor, List.of(march), "80000.00", MARCH_DUE, "OP-MIDNIGHT-" + round);
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<?> upload = executor.submit(() -> {
                    start.await(30, TimeUnit.SECONDS);
                    return submit(command);
                });
                Future<?> sweep = executor.submit(() -> {
                    start.await(30, TimeUnit.SECONDS);
                    return overdueTransition.markAsOverdueWhenPastDueDate(march, LocalDate.of(2027, 3, 11));
                });
                start.countDown();
                upload.get(60, TimeUnit.SECONDS);
                sweep.get(60, TimeUnit.SECONDS);
            } finally {
                executor.shutdownNow();
            }

            String status = paymentStatusOfInvoice(march);
            boolean markedUnderReview = jdbc.queryForObject("select marked_under_review from payment_checkout_item where invoice_id = ?", Boolean.class, march);
            if (status.equals("PAYMENT_UNDER_REVIEW")) {
                assertThat(markedUnderReview).isTrue();
                protectedWins++;
            } else {
                assertThat(status).isEqualTo("OVERDUE");
                assertThat(markedUnderReview).isFalse();
                sweepWins++;
            }
        }
        assertThat(protectedWins + sweepWins).isEqualTo(8);
        invariants.assertAllBillingInvariantsHold();
    }
}
