package com.red_escuela.backend.billing.invoice.operation;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingRequest;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingResult;
import com.red_escuela.backend.billing.entity.ScholarshipScope;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Percentage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceIssuingReviewFixesIT extends AbstractInvoiceIssuingIT {

    private Percentage percent(String points) {
        return Percentage.ofPoints(new BigDecimal(points));
    }

    private String firstDenialCode(InvoiceIssuingResult result) {
        assertThat(result).isInstanceOf(InvoiceIssuingResult.InvoiceIssuingRejected.class);
        return ((InvoiceIssuingResult.InvoiceIssuingRejected) result).evaluationResult().firstDenial().orElseThrow().code();
    }

    @Test
    @Tag("D-009")
    void theSameIdempotencyKeyOfTwoStudentsIssuesTwoSeparateInvoices() {
        var offering = factory.newProgram(Currency.ARS, 80000, 50000);
        var ana = factory.newLearner(offering, null);
        var beto = factory.newLearner(offering, null);

        var first = issued(runner.issueInvoiceInNewTransaction(adHoc(offering, ana, FeeType.EXAM_FEE, "5000.00", "shared-key")));
        var second = issued(runner.issueInvoiceInNewTransaction(adHoc(offering, beto, FeeType.EXAM_FEE, "7000.00", "shared-key")));

        assertThat(first.getId()).isNotEqualTo(second.getId());
        assertThat(first.getStudentCurrentAccountId()).isNotEqualTo(second.getStudentCurrentAccountId());
        assertThat(second.getAmounts().total().value().amount()).isEqualByComparingTo("7000.00");
        assertThat(runner.issueInvoiceInNewTransaction(adHoc(offering, beto, FeeType.EXAM_FEE, "7000.00", "shared-key")))
                .isEqualTo(new InvoiceIssuingResult.InvoiceAlreadyIssued(second.getId()));
    }

    @Test
    @Tag("D-097")
    void anEnrollmentOfAnotherStudentIsRejectedAndNothingIsWritten() {
        var offering = factory.newProgram(Currency.ARS, 80000, 50000);
        var ana = factory.newLearner(offering, null);
        var beto = factory.newLearner(offering, null);
        var otherOffering = factory.newProgram(Currency.ARS, 1000, 500);
        InvoiceIssuingRequest anasEnrollmentForBeto = new InvoiceIssuingRequest(beto.studentId(), ana.programEnrollmentId(), null, offering.programId(), null, null,
                FeeType.MONTHLY_INSTALLMENT, YearMonth.of(2027, 3), LocalDate.of(2027, 3, 11), LocalDate.of(2027, 3, 1), InvoiceIssuingReason.MONTHLY_RUN, null,
                null, null, false, null, null, null, null, null, ACTOR);
        InvoiceIssuingRequest wrongOffering = new InvoiceIssuingRequest(ana.studentId(), ana.programEnrollmentId(), null, otherOffering.programId(), null, null,
                FeeType.MONTHLY_INSTALLMENT, YearMonth.of(2027, 3), LocalDate.of(2027, 3, 11), LocalDate.of(2027, 3, 1), InvoiceIssuingReason.MONTHLY_RUN, null,
                null, null, false, null, null, null, null, null, ACTOR);
        InvoiceIssuingRequest noEnrollment = new InvoiceIssuingRequest(ana.studentId(), null, null, offering.programId(), null, null, FeeType.MONTHLY_INSTALLMENT,
                YearMonth.of(2027, 3), LocalDate.of(2027, 3, 11), LocalDate.of(2027, 3, 1), InvoiceIssuingReason.MONTHLY_RUN, null, null, null, false, null, null,
                null, null, null, ACTOR);

        assertThat(firstDenialCode(runner.issueInvoiceInNewTransaction(anasEnrollmentForBeto))).isEqualTo("ENROLLMENT_RECIPIENT_MISMATCH");
        assertThat(firstDenialCode(runner.issueInvoiceInNewTransaction(wrongOffering))).isEqualTo("ENROLLMENT_RECIPIENT_MISMATCH");
        assertThat(firstDenialCode(runner.issueInvoiceInNewTransaction(noEnrollment))).isEqualTo("ENROLLMENT_REQUIRED");
        assertThat(entriesOf(ana.studentId(), Currency.ARS)).isEmpty();
        assertThat(entriesOf(beto.studentId(), Currency.ARS)).isEmpty();
    }

    @Test
    @Tag("D-009")
    @Tag("D-021")
    void theEnrollmentFeeIssuedAtAdmissionIsNotChargedAgainWhenTheEnrollmentAppears() {
        var offering = factory.newProgram(Currency.ARS, 80000, 50000);
        Long template = factory.newTemplate(offering, 2027);
        var learner = factory.newLearner(offering, null);
        var admission = issued(runner.issueInvoiceInNewTransaction(enrollmentFee(offering, learner, template, 1, YearMonth.of(2027, 3), LocalDate.of(2027, 3, 11), null,
                false, InvoiceIssuingReason.PROGRAM_ADMISSION)));

        InvoiceIssuingResult afterEnrolling = runner.issueInvoiceInNewTransaction(enrollmentFee(offering, learner, template, 1, YearMonth.of(2027, 3),
                LocalDate.of(2027, 3, 11), null, false, InvoiceIssuingReason.MONTHLY_RUN));
        InvoiceIssuingResult nextSlot = runner.issueInvoiceInNewTransaction(enrollmentFee(offering, learner, template, 2, YearMonth.of(2027, 8),
                LocalDate.of(2027, 8, 11), null, false, InvoiceIssuingReason.MONTHLY_RUN));

        assertThat(afterEnrolling).isEqualTo(new InvoiceIssuingResult.InvoiceAlreadyIssued(admission.getId()));
        assertThat(nextSlot).isInstanceOf(InvoiceIssuingResult.InvoiceIssued.class);
        assertThat(entriesOf(learner.studentId(), Currency.ARS)).hasSize(2);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-074")
    void aPromotionCapCountsPerStudentEvenWithoutAnEnrollment() {
        var offering = factory.newProgram(Currency.ARS, 80000, 50000);
        Long template = factory.newTemplate(offering, 2027);
        var applicant = factory.newLearner(offering, null);
        Long benefitId = factory.redeemPromotion(offering, applicant, FeeType.ENROLLMENT_FEE, percent("30"), 1);

        var slot1 = issued(runner.issueInvoiceInNewTransaction(enrollmentFee(offering, applicant, template, 1, YearMonth.of(2027, 3), LocalDate.of(2027, 3, 11), null,
                false, InvoiceIssuingReason.PROGRAM_ADMISSION)));
        var slot2 = issued(runner.issueInvoiceInNewTransaction(enrollmentFee(offering, applicant, template, 2, YearMonth.of(2027, 8), LocalDate.of(2027, 3, 11), null,
                false, InvoiceIssuingReason.PROGRAM_ADMISSION)));

        assertThat(slot1.getLines().get(0).getDiscount().getStudentDiscountId()).isEqualTo(benefitId);
        assertThat(slot1.getLines().get(0).getDiscount().describeInstallmentProgress()).contains("1 de 1");
        assertThat(slot2.getLines().get(0).getDiscount()).isNull();
    }

    @Test
    @Tag("D-074")
    void aPromotionOfTwoInstallmentsDiscountsExactlyTwoOfAllTheIssuedScope() {
        var offering = factory.newProgram(Currency.ARS, 80000, 50000);
        var learner = factory.newLearner(offering, null);
        factory.redeemPromotion(offering, learner, FeeType.MONTHLY_INSTALLMENT, percent("30"), 2);

        List<Boolean> discounted = new java.util.ArrayList<>();
        for (int month = 3; month <= 8; month++) {
            var invoice = issued(runner.issueInvoiceInNewTransaction(installment(offering, learner, YearMonth.of(2027, month), LocalDate.of(2027, month, 11), LocalDate.of(2027, 3, 1))));
            discounted.add(invoice.getLines().get(0).getDiscount() != null);
        }

        assertThat(discounted).containsExactly(true, true, false, false, false, false);
    }

    @Test
    @Tag("D-073")
    void theScholarshipInstallmentIsLinkedAndTheProgressSkipsCancelledOnes() {
        var offering = factory.newProgram(Currency.ARS, 80000, 50000);
        var learner = factory.newLearner(offering, null);
        Long scholarshipId = factory.grantScholarship(offering, learner, percent("50"), 4, ScholarshipScope.INSTALLMENTS);
        List<Long> installmentIds = jdbc.queryForList("select id from scholarship_installment where scholarship_id = ? order by id", Long.class, scholarshipId);
        jdbc.update("update scholarship_installment set status = 'CANCELLED' where id = ?", installmentIds.get(0));

        var march = issued(runner.issueInvoiceInNewTransaction(installment(offering, learner, YearMonth.of(2027, 3), LocalDate.of(2027, 3, 11), LocalDate.of(2027, 3, 1))));
        var april = issued(runner.issueInvoiceInNewTransaction(installment(offering, learner, YearMonth.of(2027, 4), LocalDate.of(2027, 4, 11), LocalDate.of(2027, 4, 1))));

        assertThat(march.getLines().get(0).getDiscount().describeInstallmentProgress()).contains("1 de 3");
        assertThat(march.getLines().get(0).getDiscount().getSourceInstallmentId()).isEqualTo(installmentIds.get(1));
        assertThat(april.getLines().get(0).getDiscount().describeInstallmentProgress()).contains("2 de 3");
        assertThat(april.getLines().get(0).getDiscount().getSourceInstallmentId()).isEqualTo(installmentIds.get(2));
        assertThat(jdbc.queryForObject("select status from scholarship_installment where id = ?", String.class, installmentIds.get(1))).isEqualTo("APPLIED");
        assertThat(jdbc.queryForObject("select status from scholarship_installment where id = ?", String.class, installmentIds.get(3))).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("select source_installment_id from invoice_discount where invoice_line_id = ?", Long.class, march.getLines().get(0).getId()))
                .isEqualTo(installmentIds.get(1));
    }

    @Test
    @Tag("D-002")
    void anEnrollmentFeeWithoutSlotIsRejectedWithItsCodeInsteadOfFailing() {
        var offering = factory.newProgram(Currency.ARS, 80000, 50000);
        var learner = factory.newLearner(offering, null);
        InvoiceIssuingRequest withoutSlot = new InvoiceIssuingRequest(learner.studentId(), learner.programEnrollmentId(), null, offering.programId(), null, null,
                FeeType.ENROLLMENT_FEE, YearMonth.of(2027, 3), LocalDate.of(2027, 3, 11), LocalDate.of(2027, 3, 1), InvoiceIssuingReason.MONTHLY_RUN, null, null, null,
                false, null, null, null, null, null, ACTOR);

        assertThat(firstDenialCode(runner.issueInvoiceInNewTransaction(withoutSlot))).isEqualTo("ENROLLMENT_FEE_SLOT_REQUIRED");
    }
}
