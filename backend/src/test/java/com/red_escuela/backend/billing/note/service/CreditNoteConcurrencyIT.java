package com.red_escuela.backend.billing.note.service;

import com.red_escuela.backend.testsupport.SlowTest;
import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.billing.note.domain.CreditNoteDestination;
import com.red_escuela.backend.billing.note.domain.CreditNoteLineAmount;
import com.red_escuela.backend.billing.note.domain.CreditNoteReason;
import com.red_escuela.backend.billing.note.domain.IssueCreditNoteCommand;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.exception.ConflictException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
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

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-165")
@Tag("D-192")
@SlowTest(reason = "Issues credit notes from concurrent threads to prove numbering and net limits under contention")
class CreditNoteConcurrencyIT extends AbstractCreditNoteIT {

    @Autowired
    private BillingOperationPipeline pipeline;
    @Autowired
    private IssueCreditNoteOperation operation;

    private Callable<Object> issuing(Long invoiceId, String amount, CountDownLatch start) {
        Long requester = newStaffPerson().getId();
        return () -> {
            start.await();
            return pipeline.runOperation(operation, new IssueCreditNoteCommand(invoiceId, CreditNoteReason.BILLING_ERROR, "Error",
                    List.of(new CreditNoteLineAmount(1, com.red_escuela.backend.shared.domain.Money.of(new BigDecimal(amount), com.red_escuela.backend.shared.domain.Currency.ARS))), CreditNoteDestination.DEBT_OFFSET, requester, true, null));
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
    void twoCreditNotesOnTheSameLineNeverExceedItsNet() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Long studentId = newStudentId();
        Long accountId = newAccountId(studentId, Currency.ARS);
        Long invoiceId = issueInvoiceOfStudent(studentId, accountId, "80000.00", LocalDate.of(2027, 3, 10));
        CountDownLatch start = new CountDownLatch(1);

        List<Object> outcomes = runTogether(List.of(issuing(invoiceId, "50000.00", start), issuing(invoiceId, "50000.00", start)), start);

        assertThat(outcomes.stream().filter(ConflictException.class::isInstance)).as(outcomes.toString()).hasSize(1);
        assertThat(outcomes.stream().filter(outcome -> !(outcome instanceof Throwable))).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from credit_note where invoice_id = ?", Long.class, invoiceId)).isEqualTo(1L);
        assertThat(outstandingOf(invoiceId)).isEqualTo("30000.00");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void creditNoteNumbersOfConcurrentIssuesInDifferentAccountsNeverRepeat() throws Exception {
        List<Long> invoices = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Long studentId = newStudentId();
            invoices.add(issueInvoiceOfStudent(studentId, newAccountId(studentId, Currency.ARS), "80000.00", LocalDate.of(2027, 3, 10)));
        }
        CountDownLatch start = new CountDownLatch(1);

        List<Object> outcomes = runTogether(invoices.stream().map(invoiceId -> issuing(invoiceId, "1000.00", start)).toList(), start);

        assertThat(outcomes).noneMatch(Throwable.class::isInstance);
        List<String> numbers = jdbc.queryForList("select credit_note_number from credit_note where invoice_id in (" + String.join(",", invoices.stream().map(String::valueOf).toList()) + ")",
                String.class);
        assertThat(numbers).hasSize(4).doesNotHaveDuplicates().allMatch(number -> number.matches("NC-\\d{8}"));
    }
}
