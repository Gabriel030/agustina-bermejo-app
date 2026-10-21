package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.billing.payment.domain.BankTransferReceiptSubmissionResult;
import com.red_escuela.backend.billing.payment.exception.PossibleDuplicateBankTransferException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-269")
class DuplicateBankTransferIT extends AbstractBankTransferReceiptIT {

    @org.springframework.beans.factory.annotation.Autowired
    TransactionTemplate transaction;

    @Test
    void sameNumberDateAndAmountFromAnotherStudentIsRejected() {
        Debtor first = debtor();
        Debtor second = debtor();
        Long firstInvoice = invoiceOf(first, "80000.00", MARCH_DUE);
        Long secondInvoice = invoiceOf(second, "80000.00", MARCH_DUE);
        submit(command(first, List.of(firstInvoice), "80000.00", TODAY, "OP-SAME"));
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        int filesBefore = storedFiles.get();

        assertThatThrownBy(() -> submit(command(second, List.of(secondInvoice), "80000.00", TODAY, "OP-SAME"))).isInstanceOf(PossibleDuplicateBankTransferException.class);

        assertThat(paymentStatusOfInvoice(secondInvoice)).isEqualTo("PENDING");
        assertThat(storedFiles.get()).isEqualTo(filesBefore);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void theSameStudentUploadingTheSameTransferTwiceIsRejected() {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        Long april = invoiceOf(debtor, "80000.00", APRIL_DUE);
        submit(command(debtor, List.of(march), "80000.00", TODAY, "OP-AGAIN"));

        assertThatThrownBy(() -> submit(command(debtor, List.of(april), "80000.00", TODAY, "OP-AGAIN"))).isInstanceOf(PossibleDuplicateBankTransferException.class);
    }

    @Test
    void aDifferentNumberDateOrAmountIsNotADuplicate() {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        Long april = invoiceOf(debtor, "80000.00", APRIL_DUE);
        Long may = invoiceOf(debtor, "80000.00", LocalDate.of(2027, 5, 10));
        Long june = invoiceOf(debtor, "80000.00", LocalDate.of(2027, 6, 10));
        submit(command(debtor, List.of(march), "80000.00", TODAY, "OP-X"));

        submit(command(debtor, List.of(april), "80000.00", TODAY, "OP-Y"));
        submit(command(debtor, List.of(may), "80000.00", TODAY.minusDays(1), "OP-X"));
        submit(command(debtor, List.of(june), "80000.01", TODAY, "OP-X"));

        assertThat(jdbc.queryForObject("select count(*) from payment where student_current_account_id = ?", Long.class, debtor.accountId())).isEqualTo(4L);
    }

    @Test
    void rejectedPaymentDoesNotBlockTheSameTransfer() {
        Debtor first = debtor();
        Debtor second = debtor();
        Long firstInvoice = invoiceOf(first, "80000.00", MARCH_DUE);
        Long secondInvoice = invoiceOf(second, "80000.00", MARCH_DUE);
        BankTransferReceiptSubmissionResult rejected = submit(command(first, List.of(firstInvoice), "80000.00", TODAY, "OP-REJECTED"));
        jdbc.update("update payment set status = 'REJECTED', rejection_reason = 'Rechazado de prueba' where id = ?", rejected.paymentId());

        BankTransferReceiptSubmissionResult again = submit(command(second, List.of(secondInvoice), "80000.00", TODAY, "OP-REJECTED"));

        assertThat(again.paymentId()).isNotEqualTo(rejected.paymentId());
    }

    @Test
    void uniqueIndexRejectsDuplicateWhenPolicyIsBypassed() throws Exception {
        Debtor first = debtor();
        Debtor second = debtor();
        Long firstInvoice = invoiceOf(first, "80000.00", MARCH_DUE);
        Long secondInvoice = invoiceOf(second, "80000.00", MARCH_DUE);
        CountDownLatch firstInserted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> holder = executor.submit(() -> transaction.executeWithoutResult(status -> {
                submit(command(first, List.of(firstInvoice), "80000.00", TODAY, "OP-RACE"));
                firstInserted.countDown();
                await(releaseFirst);
            }));
            assertThat(firstInserted.await(30, TimeUnit.SECONDS)).isTrue();
            Future<?> racer = executor.submit(() -> submit(command(second, List.of(secondInvoice), "80000.00", TODAY, "OP-RACE")));
            waitUntilBlockedOnALock();
            releaseFirst.countDown();

            holder.get(30, TimeUnit.SECONDS);
            assertThatThrownBy(() -> racer.get(30, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(PossibleDuplicateBankTransferException.class);
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }

        assertThat(jdbc.queryForObject("select count(*) from payment where method_details ->> 'operationNumber' = 'OP-RACE'", Long.class)).isEqualTo(1L);
        assertThat(paymentStatusOfInvoice(secondInvoice)).isEqualTo("PENDING");
    }

    @Test
    void databaseRejectsADuplicateInsertedDirectly() {
        Debtor debtor = debtor();
        Long march = invoiceOf(debtor, "80000.00", MARCH_DUE);
        BankTransferReceiptSubmissionResult result = submit(command(debtor, List.of(march), "80000.00", TODAY, "OP-DIRECT"));

        assertThatThrownBy(() -> jdbc.update("insert into payment (student_current_account_id, currency, status, gateway, method_type, amount, amount_currency, refunded_amount, "
                + "refunded_amount_currency, registered_at, started_at, method_details, registered_by_person_id, idempotency_key) "
                + "select student_current_account_id, currency, status, gateway, method_type, amount, amount_currency, refunded_amount, refunded_amount_currency, "
                + "registered_at, started_at, method_details, registered_by_person_id, 'direct-duplicate' from payment where id = ?", result.paymentId()))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("uk_payment_bank_transfer_duplicate");
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(60, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void waitUntilBlockedOnALock() {
        org.awaitility.Awaitility.await().atMost(20, TimeUnit.SECONDS).until(() -> jdbc.queryForObject(
                "select count(*) from pg_stat_activity where wait_event_type = 'Lock' and datname = current_database()", Long.class) > 0);
    }
}
