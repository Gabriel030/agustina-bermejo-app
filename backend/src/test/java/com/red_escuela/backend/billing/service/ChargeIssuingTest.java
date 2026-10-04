package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.entity.CohortIntake;
import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.IssueReason;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.entity.PaymentPlanOption;
import com.red_escuela.backend.billing.issuing.ChargeIssuer;
import com.red_escuela.backend.billing.issuing.ChargeRequest;
import com.red_escuela.backend.billing.issuing.ChargeTarget;
import com.red_escuela.backend.billing.issuing.IssueResult;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.billing.entity.InvoiceStatus;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.billing.exception.InvoiceAlreadyAttachedException;
import com.red_escuela.backend.billing.exception.InvoiceAttachmentMismatchException;
import com.red_escuela.backend.billing.issuing.ChargeRequests;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChargeIssuingTest extends AbstractChargeSequenceTest {
    @Autowired
    private BillingTestTrigger trigger;
    @Autowired
    private ChargeIssuer issuer;

    private void planSelectionCharges(Long enrollmentId, YearMonth month, LocalDate today) {
        transactionTemplate.executeWithoutResult(status -> trigger.generateChargesForProgram(
                entityManager.find(ProgramEnrollment.class, enrollmentId), month, today));
    }

    @Autowired
    private UpfrontRegularizationService regularizationService;

    private com.red_escuela.backend.shared.domain.Money firstFeeDue(Long enrollmentId) {
        return transactionTemplate.execute(status -> invoices(enrollmentId, FeeType.ENROLLMENT_FEE).get(0).getAmountDue());
    }

    private List<Invoice> loaded(Long enrollmentId, FeeType type) {
        return transactionTemplate.execute(status -> {
            List<Invoice> list = invoices(enrollmentId, type);
            list.forEach(i -> {
                i.getAdjustments().size();
                i.getPriceRevision();
            });
            return list;
        });
    }

    private ChargeRequest installmentRequest(Long enrollmentId, YearMonth period, LocalDate due, IssueReason reason, FeeType type) {
        ProgramEnrollment reference = transactionTemplate.execute(status -> entityManager.find(ProgramEnrollment.class, enrollmentId));
        return new ChargeRequest(new ChargeTarget.ProgramStudent(reference), type, period, due, reason, null, null, null, false, ars(100), null, null, null);
    }

    @Test
    void t01EntryFeeOnPlanSelectionIsOneChargeForTheCohortSlotWithoutDiscount() {
        Career career = newCareer();
        Year y27 = newYear(career, 2027, 10, 1, 2, null);
        Long cohort = newCohort(y27, CohortIntake.FIRST, date(2027, 3, 1));
        today(date(2027, 2, 15));
        Long enrollment = newEnrollment(career, cohort, PaymentPlanCadence.MONTHLY, YearMonth.of(2027, 2), date(2027, 2, 15));

        planSelectionCharges(enrollment, YearMonth.of(2027, 2), date(2027, 2, 15));
        planSelectionCharges(enrollment, YearMonth.of(2027, 2), date(2027, 2, 15));

        List<Invoice> fees = loaded(enrollment, FeeType.ENROLLMENT_FEE);
        assertThat(fees).hasSize(1);
        Invoice fee = fees.get(0);
        assertThat(fee.getPeriod()).isEqualTo(YearMonth.of(2027, 3));
        assertThat(fee.getDueDate()).isEqualTo(date(2027, 3, 1));
        assertThat(fee.getIssueReason()).isEqualTo(IssueReason.PLAN_SELECTION);
        assertThat(fee.getEnrollmentFeeSlot()).isEqualTo(1);
        assertThat(fee.getTemplateId()).isEqualTo(y27.templateId());
        assertThat(fee.getAdjustments()).isEmpty();
        assertThat(fee.getPriceRevision()).isNotNull();
    }

    @Test
    void t02SecondIntakeEntryFeeCarriesTheDiscountWhenEmittedOnPlanSelection() {
        Career career = newCareer();
        Year y27 = newYear(career, 2027, 10, 2, 2, 8);
        Long cohort = newCohort(y27, CohortIntake.SECOND, date(2027, 8, 3));
        today(date(2027, 7, 20));
        Long enrollment = newEnrollment(career, cohort, PaymentPlanCadence.MONTHLY, YearMonth.of(2027, 7), date(2027, 7, 20));

        planSelectionCharges(enrollment, YearMonth.of(2027, 7), date(2027, 7, 20));

        Invoice fee = loaded(enrollment, FeeType.ENROLLMENT_FEE).get(0);
        assertThat(fee.getPeriod()).isEqualTo(YearMonth.of(2027, 8));
        assertThat(fee.getAdjustments()).hasSize(1);
        assertThat(firstFeeDue(enrollment)).isEqualTo(ars(2500));
    }

    @Test
    void t03SecondIntakeEntryFeeEmittedByTheJobAlsoCarriesTheDiscount() {
        Career career = newCareer();
        Year y27 = newYear(career, 2027, 10, 2, 2, 8);
        Long cohort = newCohort(y27, CohortIntake.SECOND, date(2027, 8, 3));
        today(date(2027, 7, 20));
        Long enrollment = newEnrollment(career, cohort, PaymentPlanCadence.MONTHLY, YearMonth.of(2027, 7), date(2027, 7, 20));

        today(date(2027, 8, 1));
        job.run();

        Invoice fee = loaded(enrollment, FeeType.ENROLLMENT_FEE).get(0);
        assertThat(fee.getIssueReason()).isEqualTo(IssueReason.MONTHLY_JOB);
        assertThat(fee.getAdjustments()).hasSize(1);
        assertThat(firstFeeDue(enrollment)).isEqualTo(ars(2500));
    }

    @Test
    void t08t09t10UpfrontIssuesEveryInstallmentOfTheScopeTogetherAndTheJobNeverRepeatsThem() {
        Career career = newCareer();
        Year y27 = newYear(career, 2027, 10, 1, 2, null);
        Long cohort = newCohort(y27, CohortIntake.FIRST, date(2027, 3, 1));
        today(date(2027, 3, 2));
        Long enrollment = newEnrollment(career, cohort, PaymentPlanCadence.UPFRONT, YearMonth.of(2027, 3), date(2027, 3, 2));
        transactionTemplate.executeWithoutResult(status -> {
            PaymentPlanOption option = entityManager.createQuery("select s.paymentPlanOption from PaymentPlanSelection s where s.programEnrollment.id = :id",
                    PaymentPlanOption.class).setParameter("id", enrollment).getSingleResult();
            option.setUpfrontDiscount(new MonetaryAdjustment.Proportional(Percentage.ofPoints(BigDecimal.TEN)));
        });

        planSelectionCharges(enrollment, YearMonth.of(2027, 3), date(2027, 3, 2));

        List<Invoice> installments = loaded(enrollment, FeeType.RECURRING_FEE);
        assertThat(installments).hasSize(10);
        assertThat(installments.stream().map(Invoice::getPeriod).toList()).containsExactly(
                YearMonth.of(2027, 3), YearMonth.of(2027, 4), YearMonth.of(2027, 5), YearMonth.of(2027, 6), YearMonth.of(2027, 7),
                YearMonth.of(2027, 8), YearMonth.of(2027, 9), YearMonth.of(2027, 10), YearMonth.of(2027, 11), YearMonth.of(2027, 12));
        assertThat(installments).allSatisfy(i -> {
            assertThat(i.getIssueReason()).isEqualTo(IssueReason.UPFRONT);
            assertThat(i.getDueDate()).isEqualTo(date(2027, 3, 12));
            assertThat(i.getBaseAmount()).isEqualTo(ars(10000));
            assertThat(i.getAdjustments()).hasSize(1);
        });

        newPrice(career, FeeType.RECURRING_FEE, 15000, date(2027, 3, 15));
        runEveryMonth(YearMonth.of(2027, 4), YearMonth.of(2027, 5), () -> {
        });
        runEveryMonth(YearMonth.of(2027, 12), YearMonth.of(2027, 12), () -> {
        });

        List<Invoice> after = loaded(enrollment, FeeType.RECURRING_FEE);
        assertThat(after).hasSize(10);
        assertThat(after).allSatisfy(i -> assertThat(i.getBaseAmount()).isEqualTo(ars(10000)));
    }

    @Test
    void t13AdHocRecurringFeeIsRejectedWithItsCodeAndNothingIsCreated() {
        Career career = newCareer();
        Year y27 = newYear(career, 2027, 10, 1, 2, null);
        Long cohort = newCohort(y27, CohortIntake.FIRST, date(2027, 3, 1));
        today(date(2027, 3, 2));
        Long enrollment = newEnrollment(career, cohort, PaymentPlanCadence.MONTHLY, YearMonth.of(2027, 3), date(2027, 3, 2));

        IssueResult result = issuer.issueInOwnTransaction(installmentRequest(enrollment, YearMonth.of(2027, 3), date(2027, 3, 2), IssueReason.AD_HOC, FeeType.RECURRING_FEE));

        assertThat(result).isInstanceOf(IssueResult.Rejected.class);
        assertThat(((IssueResult.Rejected) result).evaluation().firstDenial().orElseThrow().code()).isEqualTo("FEE_TYPE_NOT_ALLOWED_FOR_REASON");
        assertThat(invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(enrollment)).isEmpty();
    }

    @Test
    void aDueDateBeforeTheIssueDateAndAnEnrollmentFeeWithoutSlotAreRejected() {
        Career career = newCareer();
        Year y27 = newYear(career, 2027, 10, 1, 2, null);
        Long cohort = newCohort(y27, CohortIntake.FIRST, date(2027, 3, 1));
        today(date(2027, 3, 2));
        Long enrollment = newEnrollment(career, cohort, PaymentPlanCadence.MONTHLY, YearMonth.of(2027, 3), date(2027, 3, 2));

        IssueResult pastDue = issuer.issueInOwnTransaction(installmentRequest(enrollment, YearMonth.of(2027, 3), date(2027, 3, 1), IssueReason.MONTHLY_JOB, FeeType.RECURRING_FEE));
        IssueResult noSlot = issuer.issueInOwnTransaction(installmentRequest(enrollment, YearMonth.of(2027, 3), date(2027, 3, 2), IssueReason.MONTHLY_JOB, FeeType.ENROLLMENT_FEE));
        IssueResult dueToday = issuer.issueInOwnTransaction(installmentRequest(enrollment, YearMonth.of(2027, 3), date(2027, 3, 2), IssueReason.MONTHLY_JOB, FeeType.RECURRING_FEE));

        assertThat(((IssueResult.Rejected) pastDue).evaluation().firstDenial().orElseThrow().code()).isEqualTo("DUE_DATE_BEFORE_ISSUE_DATE");
        assertThat(((IssueResult.Rejected) noSlot).evaluation().firstDenial().orElseThrow().code()).isEqualTo("ENROLLMENT_FEE_SLOT_REQUIRED");
        assertThat(dueToday).isInstanceOf(IssueResult.Issued.class);
    }

    @Test
    void t07TwoConcurrentIssuancesOfTheSameChargeLeaveOneInvoiceAndNoFailure() throws Exception {
        Career career = newCareer();
        Year y27 = newYear(career, 2027, 10, 1, 2, null);
        Long cohort = newCohort(y27, CohortIntake.FIRST, date(2027, 3, 1));
        today(date(2027, 3, 2));
        Long enrollment = newEnrollment(career, cohort, PaymentPlanCadence.MONTHLY, YearMonth.of(2027, 3), date(2027, 3, 2));
        ChargeRequest request = installmentRequest(enrollment, YearMonth.of(2027, 3), date(2027, 3, 12), IssueReason.MONTHLY_JOB, FeeType.RECURRING_FEE);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<IssueResult>> futures = List.of(
                    pool.submit(() -> {
                        start.await();
                        return issuer.issueInOwnTransaction(request);
                    }),
                    pool.submit(() -> {
                        start.await();
                        return issuer.issueInOwnTransaction(request);
                    }));
            start.countDown();
            List<IssueResult> results = List.of(futures.get(0).get(), futures.get(1).get());

            assertThat(results.stream().filter(r -> r instanceof IssueResult.Issued).toList()).hasSize(1);
            assertThat(results.stream().filter(r -> r instanceof IssueResult.AlreadyIssued).toList()).hasSize(1);
            assertThat(invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(enrollment)).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
    }

    private Invoice admissionInvoice(Career career, Long cohortId) {
        return transactionTemplate.execute(status -> {
            Program program = entityManager.find(Program.class, career.programId());
            ProgramCohort cohort = entityManager.find(ProgramCohort.class, cohortId);
            IssueResult result = issuer.issueInCurrentTransaction(ChargeRequests.programAdmission(program, cohort, java.time.LocalDate.now(clockOf())));
            return ((IssueResult.Issued) result).invoice();
        });
    }

    private java.time.Clock clockOf() {
        return com.red_escuela.backend.shared.time.MutableClock.INSTANCE;
    }

    @Test
    void t04AdmissionInvoiceIsBornWithTemplateAndSlotWithoutDiscountAndTheLaterPlanSelectionDoesNotIssueAnotherEntryFee() {
        Career career = newCareer();
        Year y27 = newYear(career, 2027, 10, 2, 2, 8);
        Long cohort = newCohort(y27, CohortIntake.SECOND, date(2027, 8, 3));
        today(date(2027, 7, 10));

        Invoice admission = admissionInvoice(career, cohort);

        Invoice reloaded = transactionTemplate.execute(status -> {
            Invoice i = invoiceRepository.findById(admission.getId()).orElseThrow();
            i.getAdjustments().size();
            return i;
        });
        assertThat(reloaded.getIssueReason()).isEqualTo(IssueReason.ADMISSION);
        assertThat(reloaded.getTemplateId()).isEqualTo(y27.templateId());
        assertThat(reloaded.getEnrollmentFeeSlot()).isEqualTo(2);
        assertThat(reloaded.getPeriod()).isEqualTo(YearMonth.of(2027, 8));
        assertThat(reloaded.getDueDate()).isEqualTo(date(2027, 7, 10));
        assertThat(reloaded.getAdjustments()).isEmpty();
        assertThat(reloaded.getProgramEnrollment()).isNull();

        today(date(2027, 7, 20));
        Long enrollment = newEnrollment(career, cohort, PaymentPlanCadence.MONTHLY, YearMonth.of(2027, 7), date(2027, 7, 20));
        transactionTemplate.executeWithoutResult(status -> {
            Invoice i = invoiceRepository.findById(admission.getId()).orElseThrow();
            i.attachToEnrollment(entityManager.find(ProgramEnrollment.class, enrollment));
        });
        planSelectionCharges(enrollment, YearMonth.of(2027, 7), date(2027, 7, 20));

        List<Invoice> fees = invoices(enrollment, FeeType.ENROLLMENT_FEE);
        assertThat(fees).hasSize(1);
        assertThat(fees.get(0).getId()).isEqualTo(admission.getId());
    }

    @Test
    void t05RejectedAdmissionLeavesTheInvoiceCancelledWithItsReason() {
        Career career = newCareer();
        Year y27 = newYear(career, 2027, 10, 1, 2, null);
        Long cohort = newCohort(y27, CohortIntake.FIRST, date(2027, 3, 1));
        today(date(2027, 2, 10));
        Invoice admission = admissionInvoice(career, cohort);

        transactionTemplate.executeWithoutResult(status -> invoiceRepository.findById(admission.getId()).orElseThrow()
                .cancel(entityManager.find(com.red_escuela.backend.identity.entity.Person.class, career.directorId()), "Solicitud rechazada"));

        Invoice reloaded = invoiceRepository.findById(admission.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(InvoiceStatus.CANCELLED);
        assertThat(reloaded.getCancelledReason()).isEqualTo("Solicitud rechazada");
        assertThat(reloaded.getCancelledAt()).isNotNull();
    }

    @Test
    void t06AttachingTwiceOrAttachingANonAdmissionInvoiceIsAConflictAndChangesNothing() {
        Career career = newCareer();
        Year y27 = newYear(career, 2027, 10, 1, 2, null);
        Long cohort = newCohort(y27, CohortIntake.FIRST, date(2027, 3, 1));
        today(date(2027, 2, 10));
        Invoice admission = admissionInvoice(career, cohort);
        Long enrollment = newEnrollment(career, cohort, PaymentPlanCadence.MONTHLY, YearMonth.of(2027, 2), date(2027, 2, 10));
        transactionTemplate.executeWithoutResult(status -> invoiceRepository.findById(admission.getId()).orElseThrow()
                .attachToEnrollment(entityManager.find(ProgramEnrollment.class, enrollment)));

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> invoiceRepository.findById(admission.getId()).orElseThrow()
                .attachToEnrollment(entityManager.find(ProgramEnrollment.class, enrollment)))).isInstanceOf(InvoiceAlreadyAttachedException.class);

        IssueResult regular = issuer.issueInOwnTransaction(installmentRequest(enrollment, YearMonth.of(2027, 3), date(2027, 3, 12), IssueReason.MONTHLY_JOB, FeeType.RECURRING_FEE));
        Long regularId = ((IssueResult.Issued) regular).invoice().getId();
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> invoiceRepository.findById(regularId).orElseThrow()
                .attachToEnrollment(entityManager.find(ProgramEnrollment.class, enrollment)))).isInstanceOf(InvoiceAttachmentMismatchException.class);
        assertThat(invoiceRepository.findById(admission.getId()).orElseThrow().getProgramEnrollment().getId()).isEqualTo(enrollment);
    }

    @Test
    void q3UpfrontRegularizationDryRunListsWithoutIssuingAndTheRealRunIssuesWithTenDaysDue() {
        Career career = newCareer();
        Year y27 = newYear(career, 2027, 10, 1, 2, null);
        Long cohort = newCohort(y27, CohortIntake.FIRST, date(2027, 3, 1));
        today(date(2027, 3, 2));
        Long enrollment = newEnrollment(career, cohort, PaymentPlanCadence.UPFRONT, YearMonth.of(2027, 3), date(2027, 3, 2));

        var dry = regularizationService.dryRun().stream().filter(c -> c.enrollmentId().equals(enrollment)).toList();

        assertThat(dry).hasSize(1);
        assertThat(dry.get(0).plannedCharges()).isEqualTo(10);
        assertThat(dry.get(0).issuedCharges()).isZero();
        assertThat(invoices(enrollment, FeeType.RECURRING_FEE)).isEmpty();

        var real = regularizationService.regularize().stream().filter(c -> c.enrollmentId().equals(enrollment)).toList();

        assertThat(real.get(0).issuedCharges()).isEqualTo(10);
        List<Invoice> issued = invoices(enrollment, FeeType.RECURRING_FEE);
        assertThat(issued).hasSize(10).allSatisfy(i -> assertThat(i.getDueDate()).isEqualTo(date(2027, 3, 12)));
        assertThat(regularizationService.dryRun().stream().filter(c -> c.enrollmentId().equals(enrollment))).isEmpty();
    }
}
