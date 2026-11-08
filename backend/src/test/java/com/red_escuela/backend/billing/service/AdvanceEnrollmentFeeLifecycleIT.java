package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.advanceenrollmentfee.service.AdvanceEnrollmentFeeIssuer.AdvanceEnrollmentFeeIssuance;
import com.red_escuela.backend.billing.annualupfront.domain.AnnualUpfrontEligibility;
import com.red_escuela.backend.billing.annualupfront.usecase.QuoteAnnualUpfrontUseCase;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.note.domain.CreditNoteReason;
import com.red_escuela.backend.billing.note.dto.CancelInvoiceRequest;
import com.red_escuela.backend.billing.note.usecase.CancelInvoiceUseCase;
import com.red_escuela.backend.billing.payment.dto.RegisterCashPaymentRequest;
import com.red_escuela.backend.billing.payment.usecase.PreviewCashPaymentUseCase;
import com.red_escuela.backend.billing.payment.usecase.RegisterCashPaymentUseCase;
import com.red_escuela.backend.shared.domain.Money;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-523")
@Tag("D-530")
@Tag("D-532")
class AdvanceEnrollmentFeeLifecycleIT extends AbstractAdvanceEnrollmentFeeTest {

    @Autowired
    private PreviewCashPaymentUseCase previewCashPayment;
    @Autowired
    private RegisterCashPaymentUseCase registerCashPayment;
    @Autowired
    private CancelInvoiceUseCase cancelInvoice;
    @Autowired
    private QuoteAnnualUpfrontUseCase quoteAnnualUpfront;

    private Long studentIdOf(Long enrollment) {
        return jdbcTemplate.queryForObject("select student_id from program_enrollment where id = ?", Long.class, enrollment);
    }

    private void actAsAdministrative(Setup setup) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(String.valueOf(setup.career().directorId()), "n/a", "ROLE_ADMINISTRATIVO"));
    }

    private Invoice reload(Long invoiceId) {
        return invoiceRepository.findInvoiceWithLinesById(invoiceId).orElseThrow();
    }

    private void collectAtCounter(Setup setup, Long invoiceId) {
        Money amount = invoiceRepository.findById(invoiceId).orElseThrow().getAmounts().outstandingAmount().value();
        actAsAdministrative(setup);
        String fingerprint = previewCashPayment.previewCashPayment(studentIdOf(setup.enrollment()), amount).fingerprint();
        registerCashPayment.registerCashPayment(studentIdOf(setup.enrollment()), new RegisterCashPaymentRequest(amount, null, null, null, fingerprint),
                "advance-cash-" + System.nanoTime());
    }

    @Test
    @Tag("D-089")
    void staffCollectsTheAdvanceFeeAtTheCounterAndJanuaryDoesNotIssueAnotherOne() {
        Setup setup = september();
        actAsStaff(setup.career());
        AdvanceEnrollmentFeeIssuance issuance = issuer.issueForStaff(setup.enrollment());

        collectAtCounter(setup, issuance.invoiceId());

        assertThat(reload(issuance.invoiceId()).getPaymentStatus()).isEqualTo(InvoicePaymentStatus.PAID);
        today(date(2028, 1, 4));
        job.run();
        today(date(2028, 2, 3));
        job.run();
        assertThat(advanceInvoices(setup.enrollment())).hasSize(1);
        assertThat(invoices(setup.enrollment(), com.red_escuela.backend.billing.invoice.domain.FeeType.ENROLLMENT_FEE).stream()
                .filter(invoice -> setup.nextYear().templateId().equals(invoice.getTemplateId()))).hasSize(1);
    }

    @Test
    @Tag("D-533")
    void aDirectorCancelsAnUnpaidAdvanceFeeAndTheChargeKeyIsReleased() {
        Setup setup = september();
        actAsStaff(setup.career());
        AdvanceEnrollmentFeeIssuance issuance = issuer.issueForStaff(setup.enrollment());

        cancelInvoice.cancelOrRequestInvoiceCancellation(issuance.invoiceId(), new CancelInvoiceRequest(CreditNoteReason.BILLING_ERROR, "Generada por error"));

        assertThat(reload(issuance.invoiceId()).getPaymentStatus()).isEqualTo(InvoicePaymentStatus.CANCELLED);
    }

    @Test
    @Tag("D-533")
    void anAdvanceFeeUnderReviewIsNotCancelled() {
        Setup setup = september();
        actAsStaff(setup.career());
        AdvanceEnrollmentFeeIssuance issuance = issuer.issueForStaff(setup.enrollment());
        jdbcTemplate.update("update invoice set payment_status = 'PAYMENT_UNDER_REVIEW' where id = ?", issuance.invoiceId());

        assertThatThrownBy(() -> cancelInvoice.cancelOrRequestInvoiceCancellation(issuance.invoiceId(),
                new CancelInvoiceRequest(CreditNoteReason.BILLING_ERROR, "Error"))).isInstanceOf(RuntimeException.class);

        assertThat(reload(issuance.invoiceId()).getPaymentStatus()).isEqualTo(InvoicePaymentStatus.PAYMENT_UNDER_REVIEW);
    }

    @Test
    @Tag("D-303")
    void aDirectorEnrollmentFeeDiscountAppliesToTheAdvanceFeeLikeInJanuary() {
        Setup setup = september();
        Long personId = setup.career().directorId();
        jdbcTemplate.update("insert into student_discount (program_enrollment_id, student_id, currency, discount_type, applicable_invoice_type, origin, calculation, priority, "
                + "from_period, to_period, status, granted_at, granted_by_id, reason) values (?, ?, 'ARS', 'INDIVIDUAL', 'ENROLLMENT_FEE', 'DIRECT_GRANT', "
                + "cast('{\"type\":\"PROPORTIONAL\",\"fraction\":\"0.5\"}' as jsonb), 1, date '2028-02-01', date '2028-02-01', 'ACTIVE', now(), ?, 'motivo')",
                setup.enrollment(), studentIdOf(setup.enrollment()), personId);
        actAsStaff(setup.career());

        AdvanceEnrollmentFeeIssuance issuance = issuer.issueForStaff(setup.enrollment());

        assertThat(amountDue(reload(issuance.invoiceId()))).isEqualTo(ars(750));
    }

    @Test
    @Tag("D-520")
    void payingTheAdvanceFeeDoesNotSpendTheAnnualUpfrontOption() {
        Setup setup = september();
        actAsStaff(setup.career());
        AnnualUpfrontEligibility before = quoteAnnualUpfront.quoteAnnualUpfrontForStaff(setup.enrollment());
        AdvanceEnrollmentFeeIssuance issuance = issuer.issueForStaff(setup.enrollment());
        collectAtCounter(setup, issuance.invoiceId());

        AnnualUpfrontEligibility after = quoteAnnualUpfront.quoteAnnualUpfrontForStaff(setup.enrollment());

        assertThat(after.eligible()).isEqualTo(before.eligible());
        assertThat(after.violation()).isEqualTo(before.violation());
        if (after.violation() != null) {
            assertThat(after.violation().code()).isNotEqualTo("ANNUAL_UPFRONT_MONTHLY_ALREADY_USED");
        }
    }

    @Test
    void twoSimultaneousRequestsCreateASingleInvoice() throws Exception {
        Setup setup = september();
        actAsStudentOf(setup.enrollment());
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<AdvanceEnrollmentFeeIssuance>> results = new ArrayList<>();
        for (int attempt = 0; attempt < 2; attempt++) {
            Callable<AdvanceEnrollmentFeeIssuance> task = () -> {
                SecurityContextHolder.getContext().setAuthentication(authentication);
                start.await();
                return issuer.issueForCurrentStudent(setup.enrollment());
            };
            results.add(pool.submit(task));
        }
        start.countDown();
        List<AdvanceEnrollmentFeeIssuance> issuances = new ArrayList<>();
        for (Future<AdvanceEnrollmentFeeIssuance> result : results) {
            issuances.add(result.get());
        }
        pool.shutdown();

        assertThat(issuances).extracting(AdvanceEnrollmentFeeIssuance::invoiceId).containsOnly(issuances.get(0).invoiceId());
        assertThat(issuances).filteredOn(AdvanceEnrollmentFeeIssuance::created).hasSize(1);
        assertThat(advanceInvoices(setup.enrollment())).hasSize(1);
    }

    @Test
    @Tag("D-013")
    @Tag("D-015")
    void characterizationWhenTheEnrollmentWindowOpensInSeptemberTheNextYearFeeBlocksCurrentYearCertificatesWithoutAnyAdvanceRequest() {
        Setup setup = september(java.time.LocalDateTime.of(2027, 9, 1, 0, 0));
        today(date(2027, 3, 3));
        var enrollment = transactionTemplate.execute(status -> entityManager.find(com.red_escuela.backend.enrollment.entity.ProgramEnrollment.class, setup.enrollment()));
        assertThatThrownBy(() -> requirementService.requireForCurrentPeriod(enrollment, com.red_escuela.backend.billing.planning.domain.EnrollmentFeeRequirementScope.CERTIFICATE));
        markAllEnrollmentFees(setup.enrollment(), InvoicePaymentStatus.PAID);
        today(date(2027, 10, 10));

        assertThatThrownBy(() -> requirementService.requireForCurrentPeriod(enrollment, com.red_escuela.backend.billing.planning.domain.EnrollmentFeeRequirementScope.CERTIFICATE))
                .hasMessageContaining("year cycle " + setup.nextYear().templateId());
        assertThat(advanceInvoices(setup.enrollment())).isEmpty();
        assertThat(invoices(setup.enrollment(), com.red_escuela.backend.billing.invoice.domain.FeeType.ENROLLMENT_FEE).stream()
                .map(invoice -> invoice.getIssuingReason().name())).contains("ENROLLMENT_FEE_ON_DEMAND");
    }

    @Test
    @Tag("D-533")
    void anAdministrativeCancellationOfAnUnpaidAdvanceFeeWaitsForTheDirector() {
        Setup setup = september();
        actAsStaff(setup.career());
        AdvanceEnrollmentFeeIssuance issuance = issuer.issueForStaff(setup.enrollment());
        actAsAdministrative(setup);

        var outcome = cancelInvoice.cancelOrRequestInvoiceCancellation(issuance.invoiceId(), new CancelInvoiceRequest(CreditNoteReason.BILLING_ERROR, "Generada por error"));

        assertThat(outcome).isInstanceOf(com.red_escuela.backend.billing.operation.BillingOperationOutcome.AwaitingAuthorization.class);
        assertThat(reload(issuance.invoiceId()).getPaymentStatus()).isEqualTo(InvoicePaymentStatus.PENDING);
    }

    @Test
    @Tag("D-533")
    void aPaidAdvanceFeeCannotBeCancelled() {
        Setup setup = september();
        actAsStaff(setup.career());
        AdvanceEnrollmentFeeIssuance issuance = issuer.issueForStaff(setup.enrollment());
        collectAtCounter(setup, issuance.invoiceId());
        actAsStaff(setup.career());

        assertThatThrownBy(() -> cancelInvoice.cancelOrRequestInvoiceCancellation(issuance.invoiceId(),
                new CancelInvoiceRequest(CreditNoteReason.BILLING_ERROR, "Error"))).isInstanceOf(RuntimeException.class);

        assertThat(reload(issuance.invoiceId()).getPaymentStatus()).isEqualTo(InvoicePaymentStatus.PAID);
    }
}
