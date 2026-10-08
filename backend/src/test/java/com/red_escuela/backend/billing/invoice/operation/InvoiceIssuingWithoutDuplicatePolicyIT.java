package com.red_escuela.backend.billing.invoice.operation;

import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingContext;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingRequest;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingResult;
import com.red_escuela.backend.billing.invoice.exception.DuplicateInvoiceChargeException;
import com.red_escuela.backend.billing.invoice.policy.issuing.DueDateNotBeforeIssueDatePolicy;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.policy.Policy;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-009")
@Tag("D-021")
@Import(InvoiceIssuingWithoutDuplicatePolicyIT.PoliciesWithoutDuplicateCheck.class)
class InvoiceIssuingWithoutDuplicatePolicyIT extends AbstractInvoiceIssuingIT {

    @TestConfiguration
    static class PoliciesWithoutDuplicateCheck {
        @Bean
        @Primary
        PolicySet<InvoiceIssuingContext> policiesWithoutChargeKeyCheck() {
            List<Policy<InvoiceIssuingContext>> only = List.of(new DueDateNotBeforeIssueDatePolicy());
            return PolicySet.named("INVOICE_ISSUING", only);
        }
    }

    @Test
    void theUniqueIndexIsTheLastBarrierAndTheLoserGetsTheExistingInvoice() throws Exception {
        var offering = factory.newProgram(Currency.ARS, 80000, 50000);
        Long template = factory.newTemplate(offering, 2027);
        var applicant = factory.newLearner(offering, null);
        InvoiceIssuingRequest request = enrollmentFee(offering, applicant, template, 1, YearMonth.of(2027, 3), LocalDate.of(2027, 3, 11), null, false,
                InvoiceIssuingReason.PROGRAM_ADMISSION);
        int threads = 4;
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

        List<InvoiceIssuingResult> issued = results.stream().filter(InvoiceIssuingResult.InvoiceIssued.class::isInstance).toList();
        assertThat(issued).hasSize(1);
        Long id = ((InvoiceIssuingResult.InvoiceIssued) issued.get(0)).invoice().getId();
        assertThat(results.stream().filter(InvoiceIssuingResult.InvoiceAlreadyIssued.class::isInstance).map(result -> ((InvoiceIssuingResult.InvoiceAlreadyIssued) result).invoiceId()))
                .hasSize(threads - 1).containsOnly(id);
        assertThat(jdbc.queryForObject("select count(*) from invoice_line where charge_key = ?", Long.class, request.chargeKey().value())).isEqualTo(1L);
        assertThat(entriesOf(applicant.studentId(), Currency.ARS)).hasSize(1);
    }

    @Test
    void inTheCallerTransactionTheDatabaseDuplicateBecomesAConflict() {
        var offering = factory.newProgram(Currency.ARS, 80000, 50000);
        var learner = factory.newLearner(offering, null);
        InvoiceIssuingRequest request = installment(offering, learner, YearMonth.of(2027, 3), LocalDate.of(2027, 3, 11), LocalDate.of(2027, 3, 1));
        runner.issueInvoiceInNewTransaction(request);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> runner.issueInvoiceInCallerTransaction(request)))
                .isInstanceOf(DuplicateInvoiceChargeException.class);
        assertThat(entriesOf(learner.studentId(), Currency.ARS)).hasSize(1);
    }
}
