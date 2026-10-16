package com.red_escuela.backend.billing.note.service;

import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.billing.note.domain.CancelInvoiceCommand;
import com.red_escuela.backend.billing.note.domain.CreditNoteReason;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.exception.ConflictException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.red_escuela.backend.billing.account.domain.AccountTestData.ars;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.ACTOR;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-127")
@Tag("D-144")
class InvoiceCancellationConcurrencyIT extends AbstractInvoiceCancellationIT {

    @Autowired
    private BillingOperationPipeline pipeline;
    @Autowired
    private CancelInvoiceOperation operation;

    private Callable<Object> cancelling(Long invoiceId, CountDownLatch start) {
        Long director = newPersonId();
        return () -> {
            start.await();
            return pipeline.runOperation(operation, CancelInvoiceCommand.byPerson(invoiceId, CreditNoteReason.BILLING_ERROR, "Error", director, true));
        };
    }

    private Callable<Object> payingAndAllocating(Long accountId, String amount, CountDownLatch start) {
        return () -> {
            start.await();
            return inTransaction(() -> {
                var account = service.lockStudentCurrentAccount(accountId);
                service.recordPaymentReceivedCredit(account, BillingTestFixtures.seedApprovedPaymentRow(account.getId()), ars(amount), LocalDate.of(2027, 3, 11), "Pago");
                return allocationService.allocateAccountCreditToOldestInvoices(account, LocalDate.of(2027, 3, 11), ACTOR);
            });
        };
    }

    private List<Object> runTogether(List<Callable<Object>> tasks, CountDownLatch start) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        try {
            List<Future<Object>> futures = new ArrayList<>();
            tasks.forEach(task -> futures.add(executor.submit(task)));
            start.countDown();
            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> future : futures) {
                try {
                    outcomes.add(future.get(60, TimeUnit.SECONDS));
                } catch (ExecutionException e) {
                    outcomes.add(e.getCause());
                }
            }
            return outcomes;
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @Tag("B2-24")
    @Tag("D-091")
    void cancellationAndAllocationOnTheSameInvoiceNeverBoth() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        for (int round = 0; round < 5; round++) {
            Scenario scenario = unpaidInvoice("80000.00");
            CountDownLatch start = new CountDownLatch(1);

            List<Object> outcomes = runTogether(List.of(cancelling(scenario.invoiceId(), start), payingAndAllocating(scenario.accountId(), "80000.00", start)), start);

            assertThat(outcomes).as(outcomes.toString()).noneMatch(outcome -> outcome instanceof Throwable && !(outcome instanceof ConflictException));
            String status = paymentStatusOf(scenario.invoiceId());
            assertThat(status).isIn("CANCELLED", "PAID");
            if ("CANCELLED".equals(status)) {
                assertThat(jdbc.queryForObject("select count(*) from payment_allocation where invoice_id = ?", Long.class, scenario.invoiceId())).isZero();
                assertThat(jdbc.queryForObject("select unallocated_credit_amount from student_current_account where id = ?", java.math.BigDecimal.class, scenario.accountId()))
                        .isEqualByComparingTo("80000.00");
            } else {
                assertThat(creditNoteCountOf(scenario.invoiceId())).isZero();
                assertThat(outcomes.get(0)).isInstanceOf(ConflictException.class);
            }
        }
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("B2-25")
    void doubleCancellationIssuesOneCreditNote() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Scenario scenario = unpaidInvoice("80000.00");
        CountDownLatch start = new CountDownLatch(1);

        List<Object> outcomes = runTogether(List.of(cancelling(scenario.invoiceId(), start), cancelling(scenario.invoiceId(), start)), start);

        assertThat(outcomes.stream().filter(ConflictException.class::isInstance)).as(outcomes.toString()).hasSize(1);
        assertThat(outcomes.stream().filter(outcome -> !(outcome instanceof Throwable))).hasSize(1);
        assertThat(creditNoteCountOf(scenario.invoiceId())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from current_account_entry where student_current_account_id = ? and entry_type = 'CREDIT_NOTE_ISSUED'", Long.class,
                scenario.accountId())).isEqualTo(1L);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("B2-26")
    void lockOrderAccountThenInvoicesAvoidsDeadlock() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        for (int round = 0; round < 50; round++) {
            Long studentId = newStudentId();
            Long accountId = newAccountId(studentId, Currency.ARS);
            issueInvoiceOfStudent(studentId, accountId, "10000.00", LocalDate.of(2027, 3, 10));
            Long second = issueInvoiceOfStudent(studentId, accountId, "10000.00", LocalDate.of(2027, 4, 10));
            CountDownLatch start = new CountDownLatch(1);

            List<Object> outcomes = runTogether(List.of(cancelling(second, start), payingAndAllocating(accountId, "20000.00", start)), start);

            assertThat(outcomes).as(outcomes.toString()).noneMatch(outcome -> outcome instanceof Throwable && !(outcome instanceof ConflictException));
        }
        invariants.assertAllBillingInvariantsHold();
    }
}
