package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.advanceenrollmentfee.service.AdvanceEnrollmentFeeExpiration;
import com.red_escuela.backend.billing.advanceenrollmentfee.service.AdvanceEnrollmentFeeExpirer;
import com.red_escuela.backend.billing.advanceenrollmentfee.service.AdvanceEnrollmentFeeExpiryResult;
import com.red_escuela.backend.billing.advanceenrollmentfee.service.AdvanceEnrollmentFeeIssuer.AdvanceEnrollmentFeeIssuance;
import com.red_escuela.backend.billing.collections.service.StudentOverdueDebtFinder;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.note.service.LaterChargesCancellationOnWithdrawal;
import com.red_escuela.backend.billing.payment.dto.RegisterCashPaymentRequest;
import com.red_escuela.backend.billing.payment.usecase.PreviewCashPaymentUseCase;
import com.red_escuela.backend.billing.payment.usecase.RegisterCashPaymentUseCase;
import com.red_escuela.backend.billing.planning.domain.EnrollmentFeeRequirementScope;
import com.red_escuela.backend.billing.run.domain.BillingRunStep;
import com.red_escuela.backend.billing.run.domain.NightlyBillingTrigger;
import com.red_escuela.backend.billing.run.service.NightlyBillingRunner;
import com.red_escuela.backend.enrollment.api.WithdrawalCause;
import com.red_escuela.backend.enrollment.api.WithdrawnEnrollmentFacts;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.shared.domain.Money;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-529")
@Tag("D-534")
@Tag("D-526")
class AdvanceEnrollmentFeeExpiryIT extends AbstractAdvanceEnrollmentFeeTest {
    private static final LocalDate WINDOW_CLOSING_DAY = date(2028, 2, 20);
    private static final LocalDate NIGHT_AFTER_CLOSING = date(2028, 2, 21);

    @Autowired
    private AdvanceEnrollmentFeeExpirer expirer;
    @Autowired
    private AdvanceEnrollmentFeeExpiration expiration;
    @Autowired
    private PreviewCashPaymentUseCase previewCashPayment;
    @Autowired
    private RegisterCashPaymentUseCase registerCashPayment;
    @Autowired
    private StudentOverdueDebtFinder overdueDebtFinder;
    @Autowired
    private LaterChargesCancellationOnWithdrawal laterChargesCancellation;
    @Autowired
    private NightlyBillingRunner nightlyRunner;
    @Autowired
    private MonthlyInstallmentRowProcessor rowProcessor;

    private Long studentIdOf(Long enrollment) {
        return jdbcTemplate.queryForObject("select student_id from program_enrollment where id = ?", Long.class, enrollment);
    }

    private Long issueAsStaff(Setup setup) {
        actAsStaff(setup.career());
        AdvanceEnrollmentFeeIssuance issuance = issuer.issueForStaff(setup.enrollment());
        SecurityContextHolder.clearContext();
        return issuance.invoiceId();
    }

    private Invoice reload(Long invoiceId) {
        return invoiceRepository.findInvoiceWithLinesById(invoiceId).orElseThrow();
    }

    private void collectAtCounter(Setup setup, Long invoiceId) {
        Money amount = invoiceRepository.findById(invoiceId).orElseThrow().getAmounts().outstandingAmount().value();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(String.valueOf(setup.career().directorId()), "n/a", "ROLE_ADMINISTRATIVO"));
        String fingerprint = previewCashPayment.previewCashPayment(studentIdOf(setup.enrollment()), amount).fingerprint();
        registerCashPayment.registerCashPayment(studentIdOf(setup.enrollment()), new RegisterCashPaymentRequest(amount, null, null, null, fingerprint),
                "advance-expiry-cash-" + System.nanoTime());
        SecurityContextHolder.clearContext();
    }

    private AdvanceEnrollmentFeeExpiryResult runExpiryOn(LocalDate night) {
        today(night);
        return expirer.expireFeesWhoseWindowClosedBefore(night);
    }

    private List<BigDecimal> expiryCreditNoteTotals(Long invoiceId) {
        return jdbcTemplate.queryForList("select total_amount from credit_note where invoice_id = ? and reason = 'ADVANCE_ENROLLMENT_FEE_EXPIRED'", BigDecimal.class, invoiceId);
    }

    private long creditNotesOf(Long invoiceId) {
        return jdbcTemplate.queryForObject("select count(*) from credit_note where invoice_id = ?", Long.class, invoiceId);
    }

    private long nextYearFeeInvoicesOf(Setup setup) {
        return invoices(setup.enrollment(), FeeType.ENROLLMENT_FEE).stream().filter(invoice -> setup.nextYear().templateId().equals(invoice.getTemplateId())).count();
    }

    @Test
    void anUnpaidAdvanceFeeIsCancelledWithACreditNoteForItsTotalTheNightAfterTheWindowCloses() {
        Setup setup = september();
        Long invoiceId = issueAsStaff(setup);
        BigDecimal total = amountDue(reload(invoiceId)).amount();

        AdvanceEnrollmentFeeExpiryResult result = runExpiryOn(NIGHT_AFTER_CLOSING);

        assertThat(result.expired()).isEqualTo(1);
        assertThat(result.failedInvoiceIds()).isEmpty();
        assertThat(reload(invoiceId).getPaymentStatus()).isEqualTo(InvoicePaymentStatus.CANCELLED);
        assertThat(expiryCreditNoteTotals(invoiceId)).singleElement().satisfies(creditNoteTotal -> assertThat(creditNoteTotal).isEqualByComparingTo(total));
        assertThat(overdueDebtFinder.hasOverdueDebt(studentIdOf(setup.enrollment()), NIGHT_AFTER_CLOSING.plusMonths(2))).isFalse();
    }

    @Test
    void theBusinessDateDecidesTheExpiryEvenWhenTheClockShowsAnotherDay() {
        Setup setup = september();
        Long invoiceId = issueAsStaff(setup);
        today(date(2028, 2, 10));

        AdvanceEnrollmentFeeExpiryResult sameClockDay = expirer.expireFeesWhoseWindowClosedBefore(WINDOW_CLOSING_DAY);
        assertThat(sameClockDay.candidates()).isZero();
        AdvanceEnrollmentFeeExpiryResult laterBusinessDate = expirer.expireFeesWhoseWindowClosedBefore(NIGHT_AFTER_CLOSING);

        assertThat(laterBusinessDate.expired()).isEqualTo(1);
        assertThat(reload(invoiceId).getPaymentStatus()).isEqualTo(InvoicePaymentStatus.CANCELLED);
    }

    @Test
    void theFeeIsNotCancelledOnTheDayTheWindowClosesOnlyTheNightAfter() {
        Setup setup = september();
        Long invoiceId = issueAsStaff(setup);

        AdvanceEnrollmentFeeExpiryResult onClosingDay = runExpiryOn(WINDOW_CLOSING_DAY);

        assertThat(onClosingDay.candidates()).isZero();
        assertThat(reload(invoiceId).getPaymentStatus()).isNotEqualTo(InvoicePaymentStatus.CANCELLED);
        assertThat(creditNotesOf(invoiceId)).isZero();
        assertThat(runExpiryOn(NIGHT_AFTER_CLOSING).expired()).isEqualTo(1);
    }

    @Test
    @Tag("D-065")
    void anOverduePastDueAdvanceFeeIsCancelledToo() {
        Setup setup = september();
        Long invoiceId = issueAsStaff(setup);
        today(date(2027, 12, 20));
        jdbcTemplate.update("update invoice set payment_status = 'OVERDUE' where id = ?", invoiceId);

        assertThat(runExpiryOn(NIGHT_AFTER_CLOSING).expired()).isEqualTo(1);

        assertThat(reload(invoiceId).getPaymentStatus()).isEqualTo(InvoicePaymentStatus.CANCELLED);
    }

    @Test
    void aPaidAdvanceFeeIsNeverTouchedAndNothingIsRefunded() {
        Setup setup = september();
        Long invoiceId = issueAsStaff(setup);
        collectAtCounter(setup, invoiceId);
        long refundsBefore = jdbcTemplate.queryForObject("select count(*) from refund", Long.class);

        AdvanceEnrollmentFeeExpiryResult result = runExpiryOn(NIGHT_AFTER_CLOSING);

        assertThat(result.candidates()).isZero();
        assertThat(reload(invoiceId).getPaymentStatus()).isEqualTo(InvoicePaymentStatus.PAID);
        assertThat(creditNotesOf(invoiceId)).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from refund", Long.class)).isEqualTo(refundsBefore);
    }

    @Test
    void anAdvanceFeeWithAReceiptUnderReviewWaitsAndIsCancelledOnceTheReceiptIsRejected() {
        Setup setup = september();
        Long invoiceId = issueAsStaff(setup);
        jdbcTemplate.update("update invoice set payment_status = 'PAYMENT_UNDER_REVIEW' where id = ?", invoiceId);

        AdvanceEnrollmentFeeExpiryResult whileUnderReview = runExpiryOn(NIGHT_AFTER_CLOSING);

        assertThat(whileUnderReview.candidates()).isZero();
        assertThat(reload(invoiceId).getPaymentStatus()).isEqualTo(InvoicePaymentStatus.PAYMENT_UNDER_REVIEW);
        assertThat(creditNotesOf(invoiceId)).isZero();
        jdbcTemplate.update("update invoice set payment_status = 'PENDING' where id = ?", invoiceId);
        assertThat(runExpiryOn(NIGHT_AFTER_CLOSING.plusDays(1)).expired()).isEqualTo(1);
    }

    @Test
    void runningTheExpiryTwiceIssuesASingleCreditNote() {
        Setup setup = september();
        Long invoiceId = issueAsStaff(setup);

        runExpiryOn(NIGHT_AFTER_CLOSING);
        AdvanceEnrollmentFeeExpiryResult second = runExpiryOn(NIGHT_AFTER_CLOSING.plusDays(1));

        assertThat(second.candidates()).isZero();
        assertThat(creditNotesOf(invoiceId)).isEqualTo(1);
    }

    @Test
    void aPaymentThatWinsTheRaceAgainstTheExpiryKeepsTheInvoicePaid() {
        Setup setup = september();
        Long invoiceId = issueAsStaff(setup);
        today(NIGHT_AFTER_CLOSING);
        collectAtCounter(setup, invoiceId);

        boolean cancelled = expiration.cancelWhenWindowClosedAndUnpaid(invoiceId);

        assertThat(cancelled).isFalse();
        assertThat(reload(invoiceId).getPaymentStatus()).isEqualTo(InvoicePaymentStatus.PAID);
        assertThat(creditNotesOf(invoiceId)).isZero();
    }

    @Test
    void onlyTheFeesOfTheCareerWhoseWindowClosedAreCancelled() {
        Setup closed = september();
        Long closedInvoice = issueAsStaff(closed);
        Setup stillOpen = september();
        jdbcTemplate.update("update term set enrollment_end = ? where id = ?", java.sql.Timestamp.valueOf(date(2028, 3, 15).atTime(23, 59)), stillOpen.nextYear().termId());
        Long openInvoice = issueAsStaff(stillOpen);

        AdvanceEnrollmentFeeExpiryResult result = runExpiryOn(NIGHT_AFTER_CLOSING);

        assertThat(result.expired()).isEqualTo(1);
        assertThat(reload(closedInvoice).getPaymentStatus()).isEqualTo(InvoicePaymentStatus.CANCELLED);
        assertThat(reload(openInvoice).getPaymentStatus()).isNotEqualTo(InvoicePaymentStatus.CANCELLED);
        assertThat(runExpiryOn(date(2028, 3, 16)).expired()).isEqualTo(1);
        assertThat(reload(openInvoice).getPaymentStatus()).isEqualTo(InvoicePaymentStatus.CANCELLED);
    }

    @Test
    void theSchedulerDoesNotIssueTheFeeAgainAfterItExpired() {
        Setup setup = september();
        issueAsStaff(setup);
        runExpiryOn(NIGHT_AFTER_CLOSING);
        long feesAfterExpiry = nextYearFeeInvoicesOf(setup);

        for (LocalDate day : List.of(date(2028, 3, 1), date(2028, 3, 2), date(2028, 4, 3), date(2028, 5, 3))) {
            today(day);
            job.run();
        }
        today(date(2028, 5, 4));
        rowProcessor.issueEnrollmentFeesOnly(setup.enrollment(), java.time.YearMonth.of(2028, 5), date(2028, 5, 4));
        rowProcessor.issueForProgramEnrollmentCatchingUp(setup.enrollment(), java.time.YearMonth.of(2028, 5));

        assertThat(feesAfterExpiry).isEqualTo(1);
        assertThat(nextYearFeeInvoicesOf(setup)).isEqualTo(1);
        assertThat(advanceInvoices(setup.enrollment()).stream().filter(invoice -> invoice.getPaymentStatus() != InvoicePaymentStatus.CANCELLED)).isEmpty();
    }

    @Test
    void theFeeIsStillIssuedOnDemandWhenTheStudentLaterEnrollsInSomething() {
        Setup setup = september();
        issueAsStaff(setup);
        runExpiryOn(NIGHT_AFTER_CLOSING);
        invoices(setup.enrollment(), FeeType.ENROLLMENT_FEE).stream().filter(invoice -> !setup.nextYear().templateId().equals(invoice.getTemplateId()))
                .forEach(com.red_escuela.backend.billing.support.BillingTestFixtures::payInvoiceInFull);
        today(date(2028, 3, 10));
        ProgramEnrollment enrollment = transactionTemplate.execute(status -> entityManager.find(ProgramEnrollment.class, setup.enrollment()));

        assertThatThrownBy(() -> requirementService.requireForCurrentPeriod(enrollment, EnrollmentFeeRequirementScope.CERTIFICATE))
                .hasMessageContaining("year cycle " + setup.nextYear().templateId());

        assertThat(invoices(setup.enrollment(), FeeType.ENROLLMENT_FEE).stream()
                .filter(invoice -> setup.nextYear().templateId().equals(invoice.getTemplateId()) && invoice.getPaymentStatus() != InvoicePaymentStatus.CANCELLED)
                .map(invoice -> invoice.getIssuingReason().name())).containsExactly("ENROLLMENT_FEE_ON_DEMAND");
    }

    @Test
    @Tag("D-237")
    @Tag("D-300")
    void aNovemberWithdrawalCancelsTheUnpaidAdvanceFeeAndTheExpiryFindsNothingLeft() {
        Setup setup = september();
        Long invoiceId = issueAsStaff(setup);
        today(date(2027, 11, 22));

        transactionTemplate.executeWithoutResult(status -> laterChargesCancellation.cancelLaterChargesOf(WithdrawnEnrollmentFacts.ofProgramEnrollment(
                studentIdOf(setup.enrollment()), setup.enrollment(), date(2027, 11, 20), date(2027, 11, 22), WithdrawalCause.VOLUNTARY)));

        assertThat(reload(invoiceId).getPaymentStatus()).isEqualTo(InvoicePaymentStatus.CANCELLED);
        assertThat(creditNotesOf(invoiceId)).isEqualTo(1);
        assertThat(runExpiryOn(NIGHT_AFTER_CLOSING).candidates()).isZero();
        assertThat(creditNotesOf(invoiceId)).isEqualTo(1);
    }

    @Test
    @Tag("D-237")
    void aNovemberWithdrawalLeavesThePaidAdvanceFeeUntouchedAndWithoutRefund() {
        Setup setup = september();
        Long invoiceId = issueAsStaff(setup);
        collectAtCounter(setup, invoiceId);
        today(date(2027, 11, 22));

        transactionTemplate.executeWithoutResult(status -> laterChargesCancellation.cancelLaterChargesOf(WithdrawnEnrollmentFacts.ofProgramEnrollment(
                studentIdOf(setup.enrollment()), setup.enrollment(), date(2027, 11, 20), date(2027, 11, 22), WithdrawalCause.VOLUNTARY)));

        assertThat(reload(invoiceId).getPaymentStatus()).isEqualTo(InvoicePaymentStatus.PAID);
        assertThat(creditNotesOf(invoiceId)).isZero();
        assertThat(runExpiryOn(NIGHT_AFTER_CLOSING).candidates()).isZero();
    }

    @Test
    void theNightlyRunExecutesTheExpiryStepAfterTheAnnualUpfrontExpiry() {
        Setup setup = september();
        Long invoiceId = issueAsStaff(setup);
        today(NIGHT_AFTER_CLOSING);

        var run = nightlyRunner.runNightlyBillingSteps(NIGHT_AFTER_CLOSING, NightlyBillingTrigger.SCHEDULED).orElseThrow();

        assertThat(reload(invoiceId).getPaymentStatus()).isEqualTo(InvoicePaymentStatus.CANCELLED);
        var steps = jdbcTemplate.queryForList("select step from billing_run where nightly_billing_run_id = ? order by id", String.class, run);
        assertThat(steps).containsSubsequence(BillingRunStep.ANNUAL_UPFRONT_EXPIRY.name(), BillingRunStep.ADVANCE_ENROLLMENT_FEE_EXPIRY.name(), BillingRunStep.OVERDUE_DETECTION.name());
    }
}
