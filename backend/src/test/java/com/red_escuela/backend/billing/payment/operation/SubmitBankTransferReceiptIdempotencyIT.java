package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.billing.payment.domain.BankTransferReceiptSubmissionResult;
import com.red_escuela.backend.billing.payment.exception.IdempotencyKeyConflictException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-136")
@Tag("D-081")
class SubmitBankTransferReceiptIdempotencyIT extends AbstractBankTransferReceiptIT {

    @Test
    void sameKeySameBodyReplaysWithoutSecondPaymentOrFile() {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        SubmitBankTransferReceiptCommand command = command(debtor, List.of(march), "80000.00", TODAY, "OP-REPLAY", "key-replay", "sha-replay");
        BankTransferReceiptSubmissionResult first = submit(command);
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);

        BankTransferReceiptSubmissionResult replay = submit(command(debtor, List.of(march), "80000.00", TODAY, "OP-REPLAY", "key-replay", "sha-replay"));

        assertThat(replay).isEqualTo(first);
        assertThat(storedFiles.get()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from payment where student_current_account_id = ?", Long.class, debtor.accountId())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from bank_transfer_receipt where payment_id = ?", Long.class, first.paymentId())).isEqualTo(1L);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void sameKeyDifferentFileHashConflicts() {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        submit(command(debtor, List.of(march), "80000.00", TODAY, "OP-HASH", "key-hash", "sha-original"));

        assertThatThrownBy(() -> submit(command(debtor, List.of(march), "80000.00", TODAY, "OP-HASH", "key-hash", "sha-other")))
                .isInstanceOf(IdempotencyKeyConflictException.class);

        assertThat(storedFiles.get()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from payment where student_current_account_id = ?", Long.class, debtor.accountId())).isEqualTo(1L);
    }

    @Test
    void sameKeyDifferentAmountConflicts() {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        submit(command(debtor, List.of(march), "80000.00", TODAY, "OP-AMOUNT", "key-amount", "sha-amount"));

        assertThatThrownBy(() -> submit(command(debtor, List.of(march), "90000.00", TODAY, "OP-AMOUNT", "key-amount", "sha-amount")))
                .isInstanceOf(IdempotencyKeyConflictException.class);
    }

    @Test
    void twoThreadsSameKeyCreateOnePayment() throws Exception {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<BankTransferReceiptSubmissionResult> upload = () -> {
                SubmitBankTransferReceiptCommand command = command(debtor, List.of(march), "80000.00", TODAY, "OP-THREADS", "key-threads", "sha-threads");
                start.await(30, TimeUnit.SECONDS);
                return submit(command);
            };
            Future<BankTransferReceiptSubmissionResult> first = executor.submit(upload);
            Future<BankTransferReceiptSubmissionResult> second = executor.submit(upload);
            start.countDown();

            assertThat(first.get(60, TimeUnit.SECONDS)).isEqualTo(second.get(60, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }

        assertThat(storedFiles.get()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from payment where student_current_account_id = ?", Long.class, debtor.accountId())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from payment_checkout_item where student_current_account_id = ?", Long.class, debtor.accountId())).isEqualTo(1L);
        invariants.assertAllBillingInvariantsHold();
    }
}
