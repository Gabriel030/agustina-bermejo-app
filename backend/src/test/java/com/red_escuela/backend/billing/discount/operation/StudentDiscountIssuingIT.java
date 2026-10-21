package com.red_escuela.backend.billing.discount.operation;

import com.red_escuela.backend.billing.discount.domain.StudentDiscount;
import com.red_escuela.backend.billing.discount.domain.StudentDiscountStatus;
import com.red_escuela.backend.billing.entity.ScholarshipScope;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.note.domain.SystemCancellationOrigin;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.support.PriceFixtures;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.Percentage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-073")
@Tag("D-238")
@Tag("D-282")
@Tag("D-284")
@Tag("D-295")
@Tag("D-384")
@Tag("D-405")
@Tag("D-406")
class StudentDiscountIssuingIT extends AbstractStudentDiscountIssuingIT {

    private static final YearMonth MARCH = YearMonth.of(2027, 3);
    private static final YearMonth APRIL = YearMonth.of(2027, 4);
    private static final YearMonth MAY = YearMonth.of(2027, 5);
    private static final YearMonth JUNE = YearMonth.of(2027, 6);
    private static final YearMonth JULY = YearMonth.of(2027, 7);
    private static final YearMonth DECEMBER = YearMonth.of(2027, 12);

    @Test
    void aScholarshipFromTheNextInstallmentDiscountsAprilAndLeavesMarchUntouched() {
        issueInstallment(MARCH);
        long creditNotesBefore = jdbc.queryForObject("select count(*) from credit_note", Long.class);
        StudentDiscount scholarship = grantAsDirector(scholarship("50", APRIL, JUNE));

        issueInstallment(APRIL);

        assertThat(discountsOn(FeeType.MONTHLY_INSTALLMENT, MARCH)).isEmpty();
        Map<String, Object> april = onlyDiscountOn(FeeType.MONTHLY_INSTALLMENT, APRIL);
        assertThat(april.get("student_discount_id")).isEqualTo(scholarship.getId());
        assertThat(amount(april.get("discount_amount"))).isEqualByComparingTo("40000.00");
        assertThat(jdbc.queryForObject("select count(*) from credit_note", Long.class)).isEqualTo(creditNotesBefore);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void aMonthLostToABetterDiscountIsNotRecoveredAtTheEnd() {
        StudentDiscount scholarship = grantAsDirector(scholarship("50", MARCH, JUNE));
        StudentDiscount aprilOnly = grantAsDirector(individual(InvoiceType.MONTHLY_INSTALLMENT, percent("60"), APRIL, APRIL, "Hermano en la institución"));

        for (YearMonth month : List.of(MARCH, APRIL, MAY, JUNE, JULY)) {
            issueInstallment(month);
        }

        Map<String, Object> march = onlyDiscountOn(FeeType.MONTHLY_INSTALLMENT, MARCH);
        assertThat(march.get("student_discount_id")).isEqualTo(scholarship.getId());
        assertThat(march.get("installment_number")).isEqualTo(1);
        assertThat(march.get("installments_total")).isEqualTo(4);
        Map<String, Object> april = onlyDiscountOn(FeeType.MONTHLY_INSTALLMENT, APRIL);
        assertThat(april.get("student_discount_id")).isEqualTo(aprilOnly.getId());
        assertThat(april.get("discount_type")).isEqualTo("INDIVIDUAL");
        assertThat(amount(april.get("discount_amount"))).isEqualByComparingTo("48000.00");
        Map<String, Object> may = onlyDiscountOn(FeeType.MONTHLY_INSTALLMENT, MAY);
        assertThat(may.get("student_discount_id")).isEqualTo(scholarship.getId());
        assertThat(may.get("installment_number")).isEqualTo(3);
        assertThat(may.get("installments_total")).isEqualTo(4);
        Map<String, Object> june = onlyDiscountOn(FeeType.MONTHLY_INSTALLMENT, JUNE);
        assertThat(june.get("installment_number")).isEqualTo(4);
        assertThat(discountsOn(FeeType.MONTHLY_INSTALLMENT, JULY)).isEmpty();
        assertThat(discountRowsOf(scholarship.getId())).isEqualTo(3L);
        assertThat(discountRowsOf(aprilOnly.getId())).isEqualTo(1L);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void aDiscountThatLosesTheContestConsumesNothingAndStaysAvailable() {
        StudentDiscount scholarship = grantAsDirector(scholarship("50", MARCH, MARCH));
        StudentDiscount better = grantAsDirector(individual(InvoiceType.MONTHLY_INSTALLMENT, percent("60"), MARCH, MARCH, "Mejor"));

        issueInstallment(MARCH);

        assertThat(onlyDiscountOn(FeeType.MONTHLY_INSTALLMENT, MARCH).get("student_discount_id")).isEqualTo(better.getId());
        assertThat(discountRowsOf(scholarship.getId())).isZero();
        assertThat(discountDraftWouldStillBeActive(scholarship.getId())).isTrue();
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void anAdministrativeProposalAppliesOnlyAfterTheDirectorApproves() {
        Long requestId = proposeGrant(scholarship("50", APRIL, JUNE));

        issueInstallment(MARCH);
        assertThat(jdbc.queryForObject("select count(*) from student_discount where student_id = ?", Long.class, learner.studentId())).isZero();
        today(LocalDate.of(2027, 3, 20));
        approveAsDirector(requestId);
        issueInstallment(APRIL);

        assertThat(discountsOn(FeeType.MONTHLY_INSTALLMENT, MARCH)).isEmpty();
        assertThat(amount(onlyDiscountOn(FeeType.MONTHLY_INSTALLMENT, APRIL).get("discount_amount"))).isEqualByComparingTo("40000.00");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void aRevocationOnJuneTenthKeepsJuneAndRemovesJuly() {
        StudentDiscount scholarship = grantAsDirector(scholarship("50", MARCH, DECEMBER));
        for (YearMonth month : List.of(MARCH, APRIL, MAY, JUNE)) {
            issueInstallment(month);
        }
        today(LocalDate.of(2027, 6, 10));

        StudentDiscount revoked = revokeAsDirector(scholarship.getId(), "Cambió la situación económica");
        issueInstallment(JULY);

        assertThat(revoked.getStatus()).isEqualTo(StudentDiscountStatus.REVOKED);
        assertThat(revoked.getRevokedFromPeriod()).isEqualTo(JULY);
        assertThat(onlyDiscountOn(FeeType.MONTHLY_INSTALLMENT, JUNE).get("student_discount_id")).isEqualTo(scholarship.getId());
        assertThat(discountsOn(FeeType.MONTHLY_INSTALLMENT, JULY)).isEmpty();
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void aRevokedAndARegrantedScholarshipCoexistAndJuneTakesTheNewOne() {
        StudentDiscount first = grantAsDirector(scholarship("50", MARCH, DECEMBER));
        for (YearMonth month : List.of(MARCH, APRIL, MAY)) {
            issueInstallment(month);
        }
        today(LocalDate.of(2027, 6, 10));
        revokeAsDirector(first.getId(), "Se reevalúa el porcentaje");
        StudentDiscount second = grantAsDirector(scholarship("30", JUNE, DECEMBER));

        issueInstallment(JUNE);

        assertThat(onlyDiscountOn(FeeType.MONTHLY_INSTALLMENT, MAY).get("student_discount_id")).isEqualTo(first.getId());
        Map<String, Object> june = onlyDiscountOn(FeeType.MONTHLY_INSTALLMENT, JUNE);
        assertThat(june.get("student_discount_id")).isEqualTo(second.getId());
        assertThat(amount(june.get("discount_amount"))).isEqualByComparingTo("24000.00");
        assertThat(discountRowsOf(first.getId())).isEqualTo(3L);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void theDiscountIsComputedOnTheFinalPriceAfterTheMarketingDiscount() {
        PriceFixtures.persistPriceVersion(offering.programId(), null, FeeType.MONTHLY_INSTALLMENT, "Cuota con descuento de marketing",
                Money.of(new BigDecimal("100000"), Currency.ARS), LocalDate.of(2027, 3, 1), offering.directorId(), "PERCENTAGE", new BigDecimal("20"));
        grantAsDirector(scholarship("50", MARCH, JUNE));

        issueInstallment(MARCH);

        Map<String, Object> march = onlyDiscountOn(FeeType.MONTHLY_INSTALLMENT, MARCH);
        assertThat(amount(march.get("base_amount"))).isEqualByComparingTo("80000.00");
        assertThat(amount(march.get("discount_amount"))).isEqualByComparingTo("40000.00");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void aFixedDiscountAboveTheLineIsClippedAndTheInvoiceIsPaidAtZero() {
        grantAsDirector(individual(InvoiceType.MONTHLY_INSTALLMENT, new MonetaryAdjustment.Nominal(Money.of(new BigDecimal("100000.00"), Currency.ARS)), MARCH, MARCH,
                "Exención total"));

        Long invoiceId = issueInstallment(MARCH);

        assertThat(amount(onlyDiscountOn(FeeType.MONTHLY_INSTALLMENT, MARCH).get("discount_amount"))).isEqualByComparingTo("80000.00");
        Map<String, Object> invoice = jdbc.queryForMap("select total, payment_status, outstanding_amount from invoice where id = ?", invoiceId);
        assertThat(amount(invoice.get("total"))).isEqualByComparingTo("0.00");
        assertThat(amount(invoice.get("outstanding_amount"))).isEqualByComparingTo("0.00");
        assertThat(invoice.get("payment_status")).isEqualTo("PAID");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void theEnrollmentFeeTakesTheIndividualDiscountAndTheInstallmentTakesTheScholarship() {
        useCohortLearner(Percentage.ofPoints(BigDecimal.ZERO));
        StudentDiscount onFee = grantAsDirector(individual(InvoiceType.ENROLLMENT_FEE, percent("30"), MARCH, null, "Hermano"));
        StudentDiscount scholarship = grantAsDirector(scholarship("50", MARCH, DECEMBER));

        today(LocalDate.of(2027, 3, 1));
        issue(enrollmentFeeOf(learner, 1, MARCH, false));
        issueInstallment(MARCH);

        Map<String, Object> fee = onlyDiscountOn(FeeType.ENROLLMENT_FEE, MARCH);
        assertThat(fee.get("student_discount_id")).isEqualTo(onFee.getId());
        assertThat(fee.get("discount_type")).isEqualTo("INDIVIDUAL");
        assertThat(amount(fee.get("discount_amount"))).isEqualByComparingTo("15000.00");
        assertThat(fee.get("installment_number")).isNull();
        Map<String, Object> installment = onlyDiscountOn(FeeType.MONTHLY_INSTALLMENT, MARCH);
        assertThat(installment.get("student_discount_id")).isEqualTo(scholarship.getId());
        assertThat(installment.get("discount_type")).isEqualTo("SCHOLARSHIP");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void theIssuedDiscountKeepsTheCalculationSnapshotAndTheStudentDiscountReference() {
        StudentDiscount scholarship = grantAsDirector(scholarship("25", MARCH, JUNE));

        issueInstallment(APRIL);

        Map<String, Object> april = onlyDiscountOn(FeeType.MONTHLY_INSTALLMENT, APRIL);
        assertThat(april.get("student_discount_id")).isEqualTo(scholarship.getId());
        assertThat(april.get("installment_number")).isEqualTo(2);
        assertThat(april.get("installments_total")).isEqualTo(4);
        assertThat(april.get("source_installment_id")).isNull();
        assertThat(april.get("consumption_released_at")).isNull();
        assertThat(jdbc.queryForObject("""
                select d.calculation is not null and d.calculation = s.calculation
                from invoice_discount d join student_discount s on s.id = d.student_discount_id where d.id = (
                    select d2.id from invoice_discount d2 join invoice_line l on l.id = d2.invoice_line_id where l.program_enrollment_id = ?)
                """, Boolean.class, learner.programEnrollmentId())).isTrue();
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void aLegacyScholarshipAndAStudentDiscountNeverStack() {
        factory.grantScholarship(offering, learner, Percentage.ofPoints(new BigDecimal("30")), 3, ScholarshipScope.INSTALLMENTS);
        StudentDiscount scholarship = grantAsDirector(scholarship("30", MARCH, JUNE));

        issueInstallment(MARCH);

        Map<String, Object> march = onlyDiscountOn(FeeType.MONTHLY_INSTALLMENT, MARCH);
        assertThat(march.get("source_installment_id")).isNull();
        assertThat(march.get("student_discount_id")).isEqualTo(scholarship.getId());
        assertThat(jdbc.queryForObject("select count(*) from scholarship_installment si join scholarship s on s.id = si.scholarship_id where s.program_enrollment_id = ? and si.status = 'APPLIED'",
                Long.class, learner.programEnrollmentId())).isZero();
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void aBetterLegacyScholarshipWinsAndOnlyItsInstallmentIsConsumed() {
        factory.grantScholarship(offering, learner, Percentage.ofPoints(new BigDecimal("50")), 3, ScholarshipScope.INSTALLMENTS);
        StudentDiscount scholarship = grantAsDirector(scholarship("30", MARCH, JUNE));

        issueInstallment(MARCH);

        Map<String, Object> march = onlyDiscountOn(FeeType.MONTHLY_INSTALLMENT, MARCH);
        assertThat(march.get("source_installment_id")).isNotNull();
        assertThat(amount(march.get("discount_amount"))).isEqualByComparingTo("40000.00");
        assertThat(discountRowsOf(scholarship.getId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from scholarship_installment si join scholarship s on s.id = si.scholarship_id where s.program_enrollment_id = ? and si.status = 'APPLIED'",
                Long.class, learner.programEnrollmentId())).isEqualTo(1L);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void theIndividualEnrollmentFeeDiscountIsConsumedByTheFirstEnrollmentFeeOfTheLectiveYearOnly() {
        useCohortLearner(Percentage.ofPoints(BigDecimal.ZERO));
        StudentDiscount onFee = grantAsDirector(individual(InvoiceType.ENROLLMENT_FEE, percent("30"), APRIL, null, "Hermano"));

        today(LocalDate.of(2027, 3, 1));
        issue(enrollmentFeeOf(learner, 1, MARCH, false));
        today(LocalDate.of(2027, 8, 1));
        issue(enrollmentFeeOf(learner, 2, YearMonth.of(2027, 8), false));

        Map<String, Object> first = onlyDiscountOn(FeeType.ENROLLMENT_FEE, MARCH);
        assertThat(first.get("student_discount_id")).isEqualTo(onFee.getId());
        assertThat(amount(first.get("discount_amount"))).isEqualByComparingTo("15000.00");
        assertThat(discountsOn(FeeType.ENROLLMENT_FEE, YearMonth.of(2027, 8))).isEmpty();
        assertThat(discountRowsOf(onFee.getId())).isEqualTo(1L);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void anEnrollmentFeeDiscountThatLosesToTheEntryDiscountIsLostForTheYearAndTheSecondFeeIsFullPrice() {
        useCohortLearner(Percentage.ofPoints(new BigDecimal("80")));
        StudentDiscount onFee = grantAsDirector(individual(InvoiceType.ENROLLMENT_FEE, percent("30"), MARCH, null, "Hermano"));

        today(LocalDate.of(2027, 3, 1));
        issue(enrollmentFeeOf(learner, 1, MARCH, true));
        today(LocalDate.of(2027, 8, 1));
        issue(enrollmentFeeOf(learner, 2, YearMonth.of(2027, 8), false));

        Map<String, Object> entry = onlyDiscountOn(FeeType.ENROLLMENT_FEE, MARCH);
        assertThat(entry.get("discount_type")).isEqualTo("COHORT_ENTRY");
        assertThat(amount(entry.get("discount_amount"))).isEqualByComparingTo("40000.00");
        assertThat(discountsOn(FeeType.ENROLLMENT_FEE, YearMonth.of(2027, 8))).isEmpty();
        assertThat(discountRowsOf(onFee.getId())).isZero();
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void cancellingTheFirstEnrollmentFeeAndIssuingItAgainCarriesTheIndividualDiscountAgain() {
        useCohortLearner(Percentage.ofPoints(BigDecimal.ZERO));
        StudentDiscount onFee = grantAsDirector(individual(InvoiceType.ENROLLMENT_FEE, percent("30"), MARCH, null, "Hermano"));
        today(LocalDate.of(2027, 3, 1));
        Long first = issue(enrollmentFeeOf(learner, 1, MARCH, false));
        assertThat(onlyDiscountOn(FeeType.ENROLLMENT_FEE, MARCH).get("student_discount_id")).isEqualTo(onFee.getId());

        systemCancellation.cancelInvoiceWithoutDirector(first, SystemCancellationOrigin.FUTURE_ENROLLMENT_FEE_ON_WITHDRAWAL, "Matricula mal emitida");
        Long second = issue(enrollmentFeeOf(learner, 1, MARCH, false));

        assertThat(second).isNotEqualTo(first);
        List<Map<String, Object>> discounts = discountsOn(FeeType.ENROLLMENT_FEE, MARCH);
        assertThat(discounts).hasSize(2).allSatisfy(row -> assertThat(row.get("student_discount_id")).isEqualTo(onFee.getId()));
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void anIndividualDiscountOnRetakenSubjectsAppliesToEveryChargeOfItsPeriodWithoutACounterAndNeverOutsideIt() {
        StudentDiscount discount = grantAsDirector(individual(InvoiceType.RETAKEN_SUBJECT_FEE, percent("40"), MARCH, MARCH, "Convenio"));

        issue(adHocChargeOf(FeeType.RETAKEN_SUBJECT_FEE, MARCH, "30000.00", "retaken-1"));
        issue(adHocChargeOf(FeeType.RETAKEN_SUBJECT_FEE, MARCH, "30000.00", "retaken-2"));
        issue(adHocChargeOf(FeeType.RETAKEN_SUBJECT_FEE, APRIL, "30000.00", "retaken-3"));
        issue(adHocChargeOf(FeeType.RETAKEN_SUBJECT_FEE, JULY, "30000.00", "retaken-4"));
        issue(adHocChargeOf(FeeType.EXAM_FEE, MARCH, "10000.00", "exam-1"));

        assertThat(discountsOn(FeeType.RETAKEN_SUBJECT_FEE, MARCH)).hasSize(2).allSatisfy(row -> {
            assertThat(row.get("student_discount_id")).isEqualTo(discount.getId());
            assertThat(amount(row.get("discount_amount"))).isEqualByComparingTo("12000.00");
        });
        assertThat(discountsOn(FeeType.RETAKEN_SUBJECT_FEE, APRIL)).isEmpty();
        assertThat(discountsOn(FeeType.RETAKEN_SUBJECT_FEE, JULY)).isEmpty();
        assertThat(discountsOn(FeeType.EXAM_FEE, MARCH)).isEmpty();
        assertThat(discountRowsOf(discount.getId())).isEqualTo(2L);
        invariants.assertAllBillingInvariantsHold();
    }

    private boolean discountDraftWouldStillBeActive(Long studentDiscountId) {
        return discountRepository.findById(studentDiscountId).orElseThrow().getStatus() == StudentDiscountStatus.ACTIVE;
    }
}
