package com.red_escuela.backend.billing.discount.operation;

import com.red_escuela.backend.billing.configuration.service.BillingConfigurationReader;
import com.red_escuela.backend.billing.discount.domain.StudentDiscount;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingRequest;
import com.red_escuela.backend.billing.invoice.operation.IssuingScenarioFactory;
import com.red_escuela.backend.billing.note.domain.CreditNoteReason;
import com.red_escuela.backend.billing.usecase.cohort.UpdateCohortEntryFeeDiscountUseCase;
import com.red_escuela.backend.shared.domain.Percentage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-018")
@Tag("D-019")
@Tag("D-212")
@Tag("D-287")
@Tag("D-295")
@Tag("D-384")
class CohortEntryDiscountIT extends AbstractStudentDiscountIssuingIT {

    private static final YearMonth MARCH = YearMonth.of(2027, 3);
    private static final YearMonth AUGUST = YearMonth.of(2027, 8);

    @Autowired
    private BillingConfigurationReader configurationReader;
    @Autowired
    private UpdateCohortEntryFeeDiscountUseCase updateEntryDiscount;

    @Test
    void theEntryFeeCarriesTheCohortEntryDiscountRegisteredAsASingleUseStudentDiscount() {
        useCohortLearner(Percentage.ofPoints(new BigDecimal("30")));

        issue(enrollmentFeeOf(learner, 1, MARCH, true));

        Map<String, Object> applied = onlyDiscountOn(FeeType.ENROLLMENT_FEE, MARCH);
        assertThat(applied.get("discount_type")).isEqualTo("COHORT_ENTRY");
        assertThat(amount(applied.get("discount_amount"))).isEqualByComparingTo("15000.00");
        assertThat(amount(applied.get("base_amount"))).isEqualByComparingTo("50000.00");
        Long studentDiscountId = (Long) applied.get("student_discount_id");
        assertThat(studentDiscountId).isNotNull();
        Map<String, Object> registered = jdbc.queryForMap("""
                select origin, discount_type, applicable_invoice_type, status, installments_total, discount_definition_id, granted_by_id, configuration_version_id,
                       program_enrollment_id from student_discount where id = ?
                """, studentDiscountId);
        assertThat(registered.get("origin")).isEqualTo("COHORT_ENTRY_CONFIGURATION");
        assertThat(registered.get("discount_type")).isEqualTo("COHORT_ENTRY");
        assertThat(registered.get("applicable_invoice_type")).isEqualTo("ENROLLMENT_FEE");
        assertThat(registered.get("status")).isEqualTo("EXHAUSTED");
        assertThat(registered.get("installments_total")).isEqualTo(1);
        assertThat(registered.get("discount_definition_id")).isNull();
        assertThat(registered.get("granted_by_id")).isNull();
        assertThat(registered.get("program_enrollment_id")).isEqualTo(learner.programEnrollmentId());
        assertThat(registered.get("configuration_version_id")).isEqualTo(configurationReader.findCohortEntryVersionEffectiveOn(cohortId, LocalDate.of(2027, 3, 1)).orElseThrow().versionId());
        assertThat(jdbc.queryForList("select event, actor_process from student_discount_status_change where student_discount_id = ? order by id", studentDiscountId))
                .extracting(row -> row.get("event"), row -> row.get("actor_process"))
                .containsExactly(org.assertj.core.groups.Tuple.tuple("DISCOUNT_GRANTED", "TEST_STUDENT_DISCOUNT_ISSUING"),
                        org.assertj.core.groups.Tuple.tuple("LAST_INSTALLMENT_CONSUMED", "TEST_STUDENT_DISCOUNT_ISSUING"));
        assertThat(jdbc.queryForObject("select d.calculation = s.calculation from invoice_discount d join student_discount s on s.id = d.student_discount_id where s.id = ?",
                Boolean.class, studentDiscountId)).isTrue();
        assertThat(jdbc.queryForObject("select s.calculation::text from student_discount s where s.id = ?", String.class, studentDiscountId)).contains("0.3");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void anEntryDiscountThatLosesTheContestRegistersNoStudentDiscount() {
        useCohortLearner(Percentage.ofPoints(new BigDecimal("30")));
        StudentDiscount better = grantAsDirector(individual(InvoiceType.ENROLLMENT_FEE, percent("60"), MARCH, null, "Hermano"));

        issue(enrollmentFeeOf(learner, 1, MARCH, true));

        Map<String, Object> applied = onlyDiscountOn(FeeType.ENROLLMENT_FEE, MARCH);
        assertThat(applied.get("discount_type")).isEqualTo("INDIVIDUAL");
        assertThat(applied.get("student_discount_id")).isEqualTo(better.getId());
        assertThat(entryDiscountsOfLearner()).isZero();
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void aCohortWithoutEntryDiscountRegistersNothing() {
        useCohortLearner(Percentage.ofPoints(BigDecimal.ZERO));

        issue(enrollmentFeeOf(learner, 1, MARCH, true));

        assertThat(discountsOn(FeeType.ENROLLMENT_FEE, MARCH)).isEmpty();
        assertThat(entryDiscountsOfLearner()).isZero();
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void theEntryDiscountInForceOnThePricingDateIsTheOneRegisteredWhenTheVersionChangesOnDecemberFirst() {
        useCohortLearner(Percentage.ofPoints(new BigDecimal("10")));
        IssuingScenarioFactory.Learner second = newCohortLearner();

        today(LocalDate.of(2027, 11, 30));
        issue(entryFeeOn(learner, LocalDate.of(2027, 11, 30)));
        today(LocalDate.of(2027, 12, 1));
        authenticateAs(director);
        updateEntryDiscount.update(cohortId, Percentage.ofPoints(new BigDecimal("15")));
        issue(entryFeeOn(second, LocalDate.of(2027, 12, 1)));

        Map<String, Object> november = onlyDiscountOn(learner, FeeType.ENROLLMENT_FEE, MARCH);
        Map<String, Object> december = onlyDiscountOn(second, FeeType.ENROLLMENT_FEE, MARCH);
        assertThat(amount(november.get("discount_amount"))).isEqualByComparingTo("5000.00");
        assertThat(amount(december.get("discount_amount"))).isEqualByComparingTo("7500.00");
        assertThat(versionOf(november)).isNotEqualTo(versionOf(december));
        assertThat(versionOf(november)).isEqualTo(configurationReader.findCohortEntryVersionEffectiveOn(cohortId, LocalDate.of(2027, 11, 30)).orElseThrow().versionId());
        assertThat(versionOf(december)).isEqualTo(configurationReader.findCohortEntryVersionEffectiveOn(cohortId, LocalDate.of(2027, 12, 1)).orElseThrow().versionId());
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void theAdmissionFeeRegistersNoEntryDiscountEvenWhenTheCohortHasOne() {
        useCohortLearner(Percentage.ofPoints(new BigDecimal("50")));
        IssuingScenarioFactory.Learner applicant = newCohortLearner();
        long entryBefore = entryDiscountsOfLearner();

        issue(new InvoiceIssuingRequest(applicant.studentId(), null, null, offering.programId(), null, cohortId, FeeType.ENROLLMENT_FEE, MARCH, LocalDate.of(2027, 3, 11),
                LocalDate.of(2027, 3, 1), InvoiceIssuingReason.PROGRAM_ADMISSION, null, templateId, 1, true, null, null, null, null, null, ACTOR));

        assertThat(jdbc.queryForObject("""
                select count(*) from invoice_discount d join invoice_line l on l.id = d.invoice_line_id
                where l.fee_type = 'ENROLLMENT_FEE' and l.program_enrollment_id is null and l.charge_key like ?
                """, Long.class, "PA:" + applicant.studentId() + ":%")).isZero();
        assertThat(entryDiscountsOfLearner()).isEqualTo(entryBefore);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-405")
    void aSecondEntryFeeOfTheYearAfterTheFirstTookTheEntryDiscountCarriesNoDiscountAndRegistersNone() {
        useCohortLearner(Percentage.ofPoints(new BigDecimal("80")));
        grantAsDirector(individual(InvoiceType.ENROLLMENT_FEE, percent("30"), MARCH, null, "Hermano"));

        today(LocalDate.of(2027, 3, 1));
        issue(enrollmentFeeOf(learner, 1, MARCH, true));
        today(LocalDate.of(2027, 8, 1));
        issue(enrollmentFeeOf(learner, 2, AUGUST, false));

        assertThat(onlyDiscountOn(FeeType.ENROLLMENT_FEE, MARCH).get("discount_type")).isEqualTo("COHORT_ENTRY");
        assertThat(discountsOn(FeeType.ENROLLMENT_FEE, AUGUST)).isEmpty();
        assertThat(entryDiscountsOfLearner()).isEqualTo(1L);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-405")
    @Tag("D-416")
    void reissuingTheFirstEntryFeeWhileASecondOneIsLiveCarriesNoIndividualDiscount() {
        useCohortLearner(Percentage.ofPoints(BigDecimal.ZERO));
        StudentDiscount onFee = grantAsDirector(individual(InvoiceType.ENROLLMENT_FEE, percent("30"), MARCH, null, "Hermano"));
        today(LocalDate.of(2027, 3, 1));
        Long first = issue(enrollmentFeeOf(learner, 1, MARCH, false));
        today(LocalDate.of(2027, 8, 1));
        Long second = issue(enrollmentFeeOf(learner, 2, AUGUST, false));

        cancelAsDirector(first, CreditNoteReason.BILLING_ERROR);
        today(LocalDate.of(2027, 3, 2));
        Long reissued = issue(enrollmentFeeOf(learner, 1, MARCH, false));

        assertThat(reissued).isNotEqualTo(first);
        assertThat(discountCountOfInvoice(reissued)).isZero();
        assertThat(discountCountOfInvoice(second)).isZero();
        assertThat(discountCountOfInvoice(first)).isEqualTo(1L);
        assertThat(discountRowsOf(onFee.getId())).isEqualTo(1L);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-286")
    void reissuingAnEntryFeeCancelledByBillingErrorReusesTheSameSingleUseDiscountWithTheSameAmount() {
        useCohortLearner(Percentage.ofPoints(new BigDecimal("30")));
        today(LocalDate.of(2027, 3, 1));
        Long first = issue(enrollmentFeeOf(learner, 1, MARCH, true));
        Long entryDiscountId = (Long) onlyDiscountOn(FeeType.ENROLLMENT_FEE, MARCH).get("student_discount_id");

        cancelAsDirector(first, CreditNoteReason.BILLING_ERROR);

        assertThat(statusOf(entryDiscountId)).isEqualTo("ACTIVE");
        Long reissued = issue(enrollmentFeeOf(learner, 1, MARCH, true));

        assertThat(reissued).isNotEqualTo(first);
        assertThat(entryDiscountsOfLearner()).isEqualTo(1L);
        assertThat(statusOf(entryDiscountId)).isEqualTo("EXHAUSTED");
        List<Map<String, Object>> rows = discountsOn(FeeType.ENROLLMENT_FEE, MARCH);
        assertThat(rows).hasSize(2).allSatisfy(row -> assertThat(row.get("student_discount_id")).isEqualTo(entryDiscountId));
        assertThat(rows.get(0).get("consumption_released_at")).isNotNull();
        assertThat(rows.get(1).get("consumption_released_at")).isNull();
        assertThat(amount(rows.get(1).get("discount_amount"))).isEqualByComparingTo("15000.00");
        invariants.assertAllBillingInvariantsHold();
    }

    private String statusOf(Long studentDiscountId) {
        return jdbc.queryForObject("select status from student_discount where id = ?", String.class, studentDiscountId);
    }

    private long discountCountOfInvoice(Long invoiceId) {
        return jdbc.queryForObject("select count(*) from invoice_discount d join invoice_line l on l.id = d.invoice_line_id where l.invoice_id = ?", Long.class, invoiceId);
    }

    private InvoiceIssuingRequest entryFeeOn(IssuingScenarioFactory.Learner target, LocalDate pricingDate) {
        return new InvoiceIssuingRequest(target.studentId(), target.programEnrollmentId(), null, offering.programId(), null, cohortId, FeeType.ENROLLMENT_FEE, MARCH,
                pricingDate.plusDays(10), pricingDate, InvoiceIssuingReason.ENROLLMENT_FEE_ON_DEMAND, target.selectionId(), templateId, 1, true, null, null, null, null, null, ACTOR);
    }

    private Map<String, Object> onlyDiscountOn(IssuingScenarioFactory.Learner target, FeeType feeType, YearMonth period) {
        List<Map<String, Object>> discounts = discountsOn(target, feeType, period);
        assertThat(discounts).hasSize(1);
        return discounts.get(0);
    }

    private Long versionOf(Map<String, Object> appliedDiscount) {
        return jdbc.queryForObject("select configuration_version_id from student_discount where id = ?", Long.class, appliedDiscount.get("student_discount_id"));
    }

    private long entryDiscountsOfLearner() {
        return jdbc.queryForObject("select count(*) from student_discount where student_id = ? and origin = 'COHORT_ENTRY_CONFIGURATION'", Long.class, learner.studentId());
    }
}
