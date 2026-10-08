package com.red_escuela.backend.billing.invoice.operation;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingRequest;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingResult;
import com.red_escuela.backend.shared.domain.Currency;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-009")
@Tag("D-021")
class InvoiceIssuingConcurrencyIT extends AbstractInvoiceIssuingIT {

    private List<InvoiceIssuingResult> issueConcurrently(InvoiceIssuingRequest request, int threads) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<InvoiceIssuingResult>> futures = new ArrayList<>();
        for (int index = 0; index < threads; index++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return runner.issueInvoiceInNewTransaction(request);
            }));
        }
        ready.await();
        go.countDown();
        List<InvoiceIssuingResult> results = new ArrayList<>();
        for (Future<InvoiceIssuingResult> future : futures) {
            results.add(future.get());
        }
        pool.shutdown();
        return results;
    }

    private void assertExactlyOneIssuedAndTheRestAlreadyIssued(List<InvoiceIssuingResult> results, String chargeKey) {
        List<InvoiceIssuingResult> issued = results.stream().filter(InvoiceIssuingResult.InvoiceIssued.class::isInstance).toList();
        assertThat(issued).hasSize(1);
        Long id = ((InvoiceIssuingResult.InvoiceIssued) issued.get(0)).invoice().getId();
        assertThat(results.stream().filter(InvoiceIssuingResult.InvoiceAlreadyIssued.class::isInstance).toList())
                .hasSize(results.size() - 1).allMatch(result -> ((InvoiceIssuingResult.InvoiceAlreadyIssued) result).invoiceId().equals(id));
        assertThat(jdbc.queryForObject("select count(*) from invoice_line where charge_key = ?", Long.class, chargeKey)).isEqualTo(1L);
    }

    @Test
    void twoTriggersForTheSameEnrollmentInstallmentProduceOneInvoice() throws Exception {
        var offering = factory.newProgram(Currency.ARS, 80000, 50000);
        var learner = factory.newLearner(offering, null);
        InvoiceIssuingRequest request = installment(offering, learner, YearMonth.of(2027, 3), LocalDate.of(2027, 3, 11), LocalDate.of(2027, 3, 1));

        List<InvoiceIssuingResult> results = issueConcurrently(request, 4);

        assertExactlyOneIssuedAndTheRestAlreadyIssued(results, request.chargeKey().value());
        assertThat(entriesOf(learner.studentId(), Currency.ARS)).hasSize(1);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void twoTriggersForTheSameApplicantFeeProduceOneInvoiceEvenWithoutAnEnrollmentLock() throws Exception {
        var offering = factory.newProgram(Currency.ARS, 80000, 50000);
        Long template = factory.newTemplate(offering, 2027);
        var applicant = factory.newLearner(offering, null);
        InvoiceIssuingRequest request = enrollmentFee(offering, applicant, template, 1, YearMonth.of(2027, 3), LocalDate.of(2027, 3, 11), null, false,
                InvoiceIssuingReason.PROGRAM_ADMISSION);

        List<InvoiceIssuingResult> results = issueConcurrently(request, 4);

        assertExactlyOneIssuedAndTheRestAlreadyIssued(results, request.chargeKey().value());
        assertThat(entriesOf(applicant.studentId(), Currency.ARS)).hasSize(1);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void differentChargesOfTheSameStudentIssueConcurrentlyKeepingTheRunningBalanceConsistent() throws Exception {
        var offering = factory.newProgram(Currency.ARS, 80000, 50000);
        var learner = factory.newLearner(offering, null);
        List<InvoiceIssuingRequest> requests = new ArrayList<>();
        for (int month = 3; month <= 8; month++) {
            requests.add(installment(offering, learner, YearMonth.of(2027, month), LocalDate.of(2027, month, 11), LocalDate.of(2027, 3, 1)));
        }
        ExecutorService pool = Executors.newFixedThreadPool(requests.size());
        List<Future<InvoiceIssuingResult>> futures = requests.stream().map(request -> pool.submit(() -> runner.issueInvoiceInNewTransaction(request))).toList();
        for (Future<InvoiceIssuingResult> future : futures) {
            assertThat(future.get()).isInstanceOf(InvoiceIssuingResult.InvoiceIssued.class);
        }
        pool.shutdown();

        assertThat(entriesOf(learner.studentId(), Currency.ARS)).hasSize(6);
        assertThat(accountOf(learner.studentId(), Currency.ARS).getBalance().amount().value().amount()).isEqualByComparingTo("480000.00");
        invariants.assertAllBillingInvariantsHold();
    }
}
