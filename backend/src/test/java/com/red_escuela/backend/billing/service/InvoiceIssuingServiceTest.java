package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.entity.CohortIntake;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.entity.PaymentPlanOption;
import com.red_escuela.backend.billing.invoice.service.InvoiceIssuingService;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingRequest;
import com.red_escuela.backend.billing.invoice.domain.InvoiceRecipient;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingResult;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.billing.entity.InvoiceStatus;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.billing.exception.InvoiceAlreadyAttachedException;
import com.red_escuela.backend.billing.exception.InvoiceAttachmentMismatchException;
import com.red_escuela.backend.billing.invoice.service.InvoiceIssuingRequestFactory;
import org.junit.jupiter.api.Tag;
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

import static com.red_escuela.backend.billing.support.InvoiceAssertions.assertThatInvoice;
import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvoiceIssuingServiceTest extends AbstractInvoiceSequenceTest {
    @Autowired
    private BillingTestTrigger trigger;
    @Autowired
    private InvoiceIssuingService issuer;

    private void planSelectionCharges(Long enrollmentId, YearMonth month, LocalDate today) {
        transactionTemplate.executeWithoutResult(status -> trigger.issueInvoicesForNewPaymentPlan(
                entityManager.find(ProgramEnrollment.class, enrollmentId), month, today));
    }

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

    private InvoiceIssuingRequest installmentRequest(Long enrollmentId, YearMonth period, LocalDate due, InvoiceIssuingReason reason, FeeType type) {
        ProgramEnrollment reference = transactionTemplate.execute(status -> entityManager.find(ProgramEnrollment.class, enrollmentId));
        return new InvoiceIssuingRequest(new InvoiceRecipient.ProgramEnrollmentRecipient(reference), type, period, due, reason, null, null, null, false, ars(100), null, null, null);
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
        assertThat(fee.getIssueReason()).isEqualTo(InvoiceIssuingReason.PAYMENT_PLAN_SELECTION);
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
    @Tag("D-018")
    void t03SecondIntakeEntryFeeEmittedByTheJobAlsoCarriesTheDiscount() {
        Career career = newCareer();
        Year y27 = newYear(career, 2027, 10, 2, 2, 8);
        Long cohort = newCohort(y27, CohortIntake.SECOND, date(2027, 8, 3));
        today(date(2027, 7, 20));
        Long enrollment = newEnrollment(career, cohort, PaymentPlanCadence.MONTHLY, YearMonth.of(2027, 7), date(2027, 7, 20));

        today(date(2027, 8, 1));
        job.run();

        Invoice fee = loaded(enrollment, FeeType.ENROLLMENT_FEE).get(0);
        assertThat(fee.getIssueReason()).isEqualTo(InvoiceIssuingReason.MONTHLY_RUN);
        assertThat(fee.getAdjustments()).hasSize(1);
        assertThat(firstFeeDue(enrollment)).isEqualTo(ars(2500));
    }

    @Test
    void t13AdHocRecurringFeeIsRejectedWithItsCodeAndNothingIsCreated() {
        Career career = newCareer();
        Year y27 = newYear(career, 2027, 10, 1, 2, null);
        Long cohort = newCohort(y27, CohortIntake.FIRST, date(2027, 3, 1));
        today(date(2027, 3, 2));
        Long enrollment = newEnrollment(career, cohort, PaymentPlanCadence.MONTHLY, YearMonth.of(2027, 3), date(2027, 3, 2));

        InvoiceIssuingResult result = issuer.issueInvoiceInNewTransaction(installmentRequest(enrollment, YearMonth.of(2027, 3), date(2027, 3, 2), InvoiceIssuingReason.AD_HOC_CHARGE, FeeType.MONTHLY_INSTALLMENT));

        assertThat(result).isInstanceOf(InvoiceIssuingResult.InvoiceIssuingRejected.class);
        assertThat(((InvoiceIssuingResult.InvoiceIssuingRejected) result).evaluation().firstDenial().orElseThrow().code()).isEqualTo("FEE_TYPE_NOT_ALLOWED_FOR_REASON");
        assertThat(invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(enrollment)).isEmpty();
    }

    @Test
    void aDueDateBeforeTheIssueDateAndAnEnrollmentFeeWithoutSlotAreRejected() {
        Career career = newCareer();
        Year y27 = newYear(career, 2027, 10, 1, 2, null);
        Long cohort = newCohort(y27, CohortIntake.FIRST, date(2027, 3, 1));
        today(date(2027, 3, 2));
        Long enrollment = newEnrollment(career, cohort, PaymentPlanCadence.MONTHLY, YearMonth.of(2027, 3), date(2027, 3, 2));

        InvoiceIssuingResult pastDue = issuer.issueInvoiceInNewTransaction(installmentRequest(enrollment, YearMonth.of(2027, 3), date(2027, 3, 1), InvoiceIssuingReason.MONTHLY_RUN, FeeType.MONTHLY_INSTALLMENT));
        InvoiceIssuingResult noSlot = issuer.issueInvoiceInNewTransaction(installmentRequest(enrollment, YearMonth.of(2027, 3), date(2027, 3, 2), InvoiceIssuingReason.MONTHLY_RUN, FeeType.ENROLLMENT_FEE));
        InvoiceIssuingResult dueToday = issuer.issueInvoiceInNewTransaction(installmentRequest(enrollment, YearMonth.of(2027, 3), date(2027, 3, 2), InvoiceIssuingReason.MONTHLY_RUN, FeeType.MONTHLY_INSTALLMENT));

        assertThat(((InvoiceIssuingResult.InvoiceIssuingRejected) pastDue).evaluation().firstDenial().orElseThrow().code()).isEqualTo("DUE_DATE_BEFORE_ISSUE_DATE");
        assertThat(((InvoiceIssuingResult.InvoiceIssuingRejected) noSlot).evaluation().firstDenial().orElseThrow().code()).isEqualTo("ENROLLMENT_FEE_SLOT_REQUIRED");
        assertThat(dueToday).isInstanceOf(InvoiceIssuingResult.InvoiceIssued.class);
    }

    @Test
    void t07TwoConcurrentIssuancesOfTheSameChargeLeaveOneInvoiceAndNoFailure() throws Exception {
        Career career = newCareer();
        Year y27 = newYear(career, 2027, 10, 1, 2, null);
        Long cohort = newCohort(y27, CohortIntake.FIRST, date(2027, 3, 1));
        today(date(2027, 3, 2));
        Long enrollment = newEnrollment(career, cohort, PaymentPlanCadence.MONTHLY, YearMonth.of(2027, 3), date(2027, 3, 2));
        InvoiceIssuingRequest request = installmentRequest(enrollment, YearMonth.of(2027, 3), date(2027, 3, 12), InvoiceIssuingReason.MONTHLY_RUN, FeeType.MONTHLY_INSTALLMENT);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<InvoiceIssuingResult>> futures = List.of(
                    pool.submit(() -> {
                        start.await();
                        return issuer.issueInvoiceInNewTransaction(request);
                    }),
                    pool.submit(() -> {
                        start.await();
                        return issuer.issueInvoiceInNewTransaction(request);
                    }));
            start.countDown();
            List<InvoiceIssuingResult> results = List.of(futures.get(0).get(), futures.get(1).get());

            assertThat(results.stream().filter(r -> r instanceof InvoiceIssuingResult.InvoiceIssued).toList()).hasSize(1);
            assertThat(results.stream().filter(r -> r instanceof InvoiceIssuingResult.InvoiceAlreadyIssued).toList()).hasSize(1);
            assertThat(invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(enrollment)).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
    }

    private Invoice admissionInvoice(Career career, Long cohortId) {
        return transactionTemplate.execute(status -> {
            Program program = entityManager.find(Program.class, career.programId());
            ProgramCohort cohort = entityManager.find(ProgramCohort.class, cohortId);
            InvoiceIssuingResult result = issuer.issueInvoiceInCallerTransaction(InvoiceIssuingRequestFactory.createProgramAdmissionRequest(program, cohort, java.time.LocalDate.now(clockOf())));
            return ((InvoiceIssuingResult.InvoiceIssued) result).invoice();
        });
    }

    private java.time.Clock clockOf() {
        return com.red_escuela.backend.shared.time.MutableClock.INSTANCE;
    }

    @Test
    @Tag("D-019")
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
        assertThat(reloaded.getIssueReason()).isEqualTo(InvoiceIssuingReason.PROGRAM_ADMISSION);
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
    @Tag("contradicts-D-127")
    void t05RejectedAdmissionLeavesTheInvoiceCancelledWithItsReason() {
        Career career = newCareer();
        Year y27 = newYear(career, 2027, 10, 1, 2, null);
        Long cohort = newCohort(y27, CohortIntake.FIRST, date(2027, 3, 1));
        today(date(2027, 2, 10));
        Invoice admission = admissionInvoice(career, cohort);

        transactionTemplate.executeWithoutResult(status -> invoiceRepository.findById(admission.getId()).orElseThrow()
                .cancel(entityManager.find(com.red_escuela.backend.identity.entity.Person.class, career.directorId()), "Solicitud rechazada"));

        Invoice reloaded = invoiceRepository.findById(admission.getId()).orElseThrow();
        assertThatInvoice(reloaded).isCancelled();
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

        InvoiceIssuingResult regular = issuer.issueInvoiceInNewTransaction(installmentRequest(enrollment, YearMonth.of(2027, 3), date(2027, 3, 12), InvoiceIssuingReason.MONTHLY_RUN, FeeType.MONTHLY_INSTALLMENT));
        Long regularId = ((InvoiceIssuingResult.InvoiceIssued) regular).invoice().getId();
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> invoiceRepository.findById(regularId).orElseThrow()
                .attachToEnrollment(entityManager.find(ProgramEnrollment.class, enrollment)))).isInstanceOf(InvoiceAttachmentMismatchException.class);
        assertThat(invoiceRepository.findById(admission.getId()).orElseThrow().getProgramEnrollment().getId()).isEqualTo(enrollment);
    }

    @Test
    @Tag("D-021")
    void entryFeeIssuedBySelectionFirstAndByTheJobAfterwardsIsExactlyOneForTheSameMonth() {
        Career career = newCareer();
        Year y27 = newYear(career, 2027, 10, 1, 2, null);
        Long cohort = newCohort(y27, CohortIntake.FIRST, date(2027, 3, 1));
        today(date(2027, 2, 15));
        Long enrollment = newEnrollment(career, cohort, PaymentPlanCadence.MONTHLY, YearMonth.of(2027, 2), date(2027, 2, 15));

        planSelectionCharges(enrollment, YearMonth.of(2027, 2), date(2027, 2, 15));
        today(date(2027, 2, 20));
        job.run();
        today(date(2027, 3, 1));
        job.run();

        List<Invoice> fees = invoices(enrollment, FeeType.ENROLLMENT_FEE);
        assertThat(fees).hasSize(1);
        assertThat(fees.get(0).getPeriod()).isEqualTo(YearMonth.of(2027, 3));
        assertThat(fees.get(0).getIssueReason()).isEqualTo(InvoiceIssuingReason.PAYMENT_PLAN_SELECTION);
    }

    @Test
    @Tag("D-021")
    void entryFeeIssuedByTheJobFirstAndBySelectionAfterwardsIsExactlyOneForTheSameMonth() {
        Career career = newCareer();
        Year y27 = newYear(career, 2027, 10, 1, 2, null);
        Long cohort = newCohort(y27, CohortIntake.FIRST, date(2027, 3, 1));
        today(date(2027, 1, 5));
        Long enrollment = newEnrollment(career, cohort, PaymentPlanCadence.MONTHLY, YearMonth.of(2027, 1), date(2027, 1, 5));

        today(date(2027, 2, 1));
        job.run();
        planSelectionCharges(enrollment, YearMonth.of(2027, 2), date(2027, 2, 15));
        today(date(2027, 3, 1));
        job.run();

        List<Invoice> fees = invoices(enrollment, FeeType.ENROLLMENT_FEE);
        assertThat(fees).hasSize(1);
        assertThat(fees.get(0).getPeriod()).isEqualTo(YearMonth.of(2027, 3));
        assertThat(fees.get(0).getIssueReason()).isEqualTo(InvoiceIssuingReason.MONTHLY_RUN);
    }
}
