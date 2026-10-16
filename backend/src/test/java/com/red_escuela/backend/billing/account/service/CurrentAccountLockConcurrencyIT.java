package com.red_escuela.backend.billing.account.service;

import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.billing.account.domain.CurrentAccountEntry;
import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.shared.domain.Currency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.red_escuela.backend.billing.account.domain.AccountTestData.ars;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-144")
@Tag("D-145")
class CurrentAccountLockConcurrencyIT extends AbstractCurrentAccountIT {

    private static final LocalDate DAY = LocalDate.of(2027, 3, 10);

    private BillingInvariantChecker invariants;

    @BeforeEach
    void startBillingInvariantBaseline() {
        invariants = BillingInvariantChecker.startingNow(jdbc);
    }

    private static final class Outcome {
        Object value;
        Throwable failure;
    }

    private List<Outcome> runConcurrently(List<Callable<Object>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (Callable<Object> task : tasks) {
            futures.add(pool.submit(() -> {
                start.await();
                return task.call();
            }));
        }
        start.countDown();
        List<Outcome> outcomes = new ArrayList<>();
        for (Future<Object> future : futures) {
            Outcome outcome = new Outcome();
            try {
                outcome.value = future.get(60, TimeUnit.SECONDS);
            } catch (ExecutionException exception) {
                outcome.failure = exception.getCause();
            }
            outcomes.add(outcome);
        }
        pool.shutdown();
        return outcomes;
    }

    private Callable<Object> debitOf(Long accountId, Long invoiceId) {
        return () -> inTransaction(() -> {
            StudentCurrentAccount account = service.lockStudentCurrentAccount(accountId);
            Invoice invoice = invoiceRepository.findById(invoiceId).orElseThrow();
            return service.recordInvoiceIssuedDebit(account, invoice, DAY).orElseThrow();
        });
    }

    @Test
    void twoConcurrentDebitsAreSerializedByTheAccountLock() throws Exception {
        Long accountId = newAccountId(newStudentId(), Currency.ARS);
        Long first = newIssuedInvoiceId(accountId, Currency.ARS, "100.00");
        Long second = newIssuedInvoiceId(accountId, Currency.ARS, "250.00");

        List<Outcome> outcomes = runConcurrently(List.of(debitOf(accountId, first), debitOf(accountId, second)));

        assertThat(outcomes).allMatch(outcome -> outcome.failure == null);
        List<CurrentAccountEntry> entries = entryRepository.findByStudentCurrentAccountIdOrderBySequenceNumberAsc(accountId);
        assertThat(entries).extracting(CurrentAccountEntry::getSequenceNumber).containsExactly(1L, 2L);
        assertThat(entries.get(1).getRunningBalance().amount().value().amount())
                .isEqualByComparingTo(entries.get(0).getRunningBalance().amount().value().amount().add(entries.get(1).getAmount().value().amount()));
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void theSecondTransactionWaitsWhileTheFirstHoldsTheLock() throws Exception {
        Long accountId = newAccountId(newStudentId(), Currency.ARS);
        CountDownLatch holderHasLock = new CountDownLatch(1);
        CountDownLatch releaseHolder = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        Future<Object> holder = pool.submit(() -> inTransaction(() -> {
            StudentCurrentAccount account = service.lockStudentCurrentAccount(accountId);
            holderHasLock.countDown();
            try {
                releaseHolder.await();
            } catch (InterruptedException exception) {
                throw new IllegalStateException(exception);
            }
            return service.recordPaymentReceivedCredit(account, BillingTestFixtures.seedApprovedPaymentRow(account.getId()), ars("10.00"), DAY, "Pago");
        }));
        assertThat(holderHasLock.await(30, TimeUnit.SECONDS)).isTrue();
        Future<Long> waiter = pool.submit(() -> inTransaction(() -> service.lockStudentCurrentAccount(accountId).getLastEntrySequence()));

        Thread.sleep(500);
        assertThat(waiter.isDone()).isFalse();
        releaseHolder.countDown();

        assertThat(holder.get(30, TimeUnit.SECONDS)).isNotNull();
        assertThat(waiter.get(30, TimeUnit.SECONDS)).isEqualTo(1L);
        pool.shutdown();
    }

    @Test
    void eightMixedOperationsKeepTheRunningBalanceConsistent() throws Exception {
        Long accountId = newAccountId(newStudentId(), Currency.ARS);
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Long invoiceId = newIssuedInvoiceId(accountId, Currency.ARS, (100 + i) + ".00");
            tasks.add(debitOf(accountId, invoiceId));
            long paymentId = BillingTestFixtures.seedApprovedPaymentRow(accountId);
            String amount = (50 + i) + ".00";
            tasks.add(() -> inTransaction(() -> service.recordPaymentReceivedCredit(service.lockStudentCurrentAccount(accountId), paymentId, ars(amount), DAY, "Pago")));
        }

        List<Outcome> outcomes = runConcurrently(tasks);

        assertThat(outcomes).allMatch(outcome -> outcome.failure == null);
        assertThat(entryRepository.findByStudentCurrentAccountIdOrderBySequenceNumberAsc(accountId))
                .extracting(CurrentAccountEntry::getSequenceNumber).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void withoutTheLockTheSequenceUniqueIsTheLastBarrier() throws Exception {
        Long accountId = newAccountId(newStudentId(), Currency.ARS);
        CyclicBarrier bothLoaded = new CyclicBarrier(2);
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            long paymentId = BillingTestFixtures.seedApprovedPaymentRow(accountId);
            tasks.add(() -> inTransaction(() -> {
                StudentCurrentAccount account = accountRepository.findById(accountId).orElseThrow();
                try {
                    bothLoaded.await(30, TimeUnit.SECONDS);
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
                CurrentAccountEntry entry = account.recordPaymentReceivedCredit(paymentId, ars("10.00"), DAY, "Pago");
                accountRepository.save(account);
                return entryRepository.save(entry);
            }));
        }

        List<Outcome> outcomes = runConcurrently(tasks);

        assertThat(outcomes.stream().filter(outcome -> outcome.failure == null)).hasSize(1);
        Throwable failure = outcomes.stream().filter(outcome -> outcome.failure != null).findFirst().orElseThrow().failure;
        assertThat(failure).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(failure.getMessage()).contains("uk_current_account_entry_sequence");
        assertThat(entryRepository.findByStudentCurrentAccountIdOrderBySequenceNumberAsc(accountId)).hasSize(1);
    }

    @Test
    @Tag("D-145")
    void twoConcurrentOpeningsCreateOneAccount() throws Exception {
        Long studentId = newStudentId();
        List<Callable<Object>> tasks = List.of(
                () -> inTransaction(() -> service.findOrOpenAndLockStudentCurrentAccount(studentId, Currency.ARS).getId()),
                () -> inTransaction(() -> service.findOrOpenAndLockStudentCurrentAccount(studentId, Currency.ARS).getId()));

        List<Outcome> outcomes = runConcurrently(tasks);

        assertThat(outcomes).allMatch(outcome -> outcome.failure == null);
        assertThat(outcomes.get(0).value).isEqualTo(outcomes.get(1).value);
        assertThat(jdbc.queryForObject("select count(*) from student_current_account where student_id = ?", Integer.class, studentId)).isEqualTo(1);
    }

    @Test
    void theSameInvoiceIsDebitedOnlyOnce() throws Exception {
        Long accountId = newAccountId(newStudentId(), Currency.ARS);
        Long invoiceId = newIssuedInvoiceId(accountId, Currency.ARS, "100.00");

        List<Outcome> outcomes = runConcurrently(List.of(debitOf(accountId, invoiceId), debitOf(accountId, invoiceId)));

        assertThat(outcomes.stream().filter(outcome -> outcome.failure == null)).hasSize(1);
        Throwable failure = outcomes.stream().filter(outcome -> outcome.failure != null).findFirst().orElseThrow().failure;
        assertThat(failure).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(failure.getMessage()).contains("uk_current_account_entry_invoice_debit");
        assertThat(entryRepository.findByStudentCurrentAccountIdOrderBySequenceNumberAsc(accountId)).hasSize(1);
        invariants.assertAllBillingInvariantsHold();
    }
}
