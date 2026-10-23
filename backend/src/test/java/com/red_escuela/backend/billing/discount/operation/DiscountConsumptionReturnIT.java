package com.red_escuela.backend.billing.discount.operation;

import com.red_escuela.backend.billing.discount.domain.StudentDiscount;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.operation.IssuingScenarioFactory;
import com.red_escuela.backend.billing.note.domain.CreditNoteReason;
import com.red_escuela.backend.shared.domain.Percentage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-199")
@Tag("D-245")
@Tag("D-286")
@Tag("D-295")
class DiscountConsumptionReturnIT extends AbstractStudentDiscountIssuingIT {

    private static final YearMonth MARCH = YearMonth.of(2027, 3);
    private static final YearMonth APRIL = YearMonth.of(2027, 4);
    private static final YearMonth MAY = YearMonth.of(2027, 5);
    private static final YearMonth JUNE = YearMonth.of(2027, 6);

    @Test
    void d04aReissuedInstallmentOfAPromotionKeepsTheSameInstallmentNumberAndThePromotionDoesNotAdvance() {
        Long promotionId = factory.acquireCohortPromotion(offering, learner, FeeType.MONTHLY_INSTALLMENT, Percentage.ofPoints(new BigDecimal("10")), 3);
        issueInstallment(MARCH);
        Long april = issueInstallment(APRIL);
        assertThat(onlyDiscountOn(FeeType.MONTHLY_INSTALLMENT, APRIL).get("installment_number")).isEqualTo(2);

        cancelAsDirector(april, CreditNoteReason.BILLING_ERROR);
        issueInstallment(APRIL);

        List<Map<String, Object>> aprilRows = discountsOn(FeeType.MONTHLY_INSTALLMENT, APRIL);
        assertThat(aprilRows).hasSize(2);
        assertThat(aprilRows.get(0).get("consumption_released_at")).isNotNull();
        assertThat(aprilRows.get(1).get("consumption_released_at")).isNull();
        assertThat(aprilRows.get(1).get("installment_number")).isEqualTo(2);
        assertThat(aprilRows.get(1).get("installments_total")).isEqualTo(3);
        assertThat(liveUsesOf(promotionId)).isEqualTo(2L);
        assertThat(statusOf(promotionId)).isEqualTo("ACTIVE");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void d04bAnExhaustedPromotionBecomesActiveWhenItsLastInstallmentIsCancelledAndExhaustedAgainOnReissue() {
        Long promotionId = factory.acquireCohortPromotion(offering, learner, FeeType.MONTHLY_INSTALLMENT, Percentage.ofPoints(new BigDecimal("10")), 3);
        issueInstallment(MARCH);
        issueInstallment(APRIL);
        Long may = issueInstallment(MAY);
        assertThat(statusOf(promotionId)).isEqualTo("EXHAUSTED");

        cancelAsDirector(may, CreditNoteReason.BILLING_ERROR);

        assertThat(statusOf(promotionId)).isEqualTo("ACTIVE");
        issueInstallment(MAY);
        assertThat(statusOf(promotionId)).isEqualTo("EXHAUSTED");
        assertThat(liveInstallmentNumbersOf(promotionId)).containsExactly(1, 2, 3);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void d04cCancellingAnEarlierInstallmentAndReissuingItTakesTheFreedNumberInsteadOfAnotherOne() {
        Long promotionId = factory.acquireCohortPromotion(offering, learner, FeeType.MONTHLY_INSTALLMENT, Percentage.ofPoints(new BigDecimal("10")), 3);
        Long march = issueInstallment(MARCH);
        issueInstallment(APRIL);

        cancelAsDirector(march, CreditNoteReason.BILLING_ERROR);
        issueInstallment(MARCH);

        assertThat(discountsOn(FeeType.MONTHLY_INSTALLMENT, MARCH).get(1).get("installment_number")).isEqualTo(1);
        assertThat(liveInstallmentNumbersOf(promotionId)).containsExactly(1, 2);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void d05AScholarshipMonthCancelledByBillingErrorIsReissuedAsTheSameInstallmentOfTheRange() {
        StudentDiscount scholarship = grantAsDirector(scholarship("50", APRIL, JUNE));
        Long april = issueInstallment(APRIL);
        assertThat(onlyDiscountOn(FeeType.MONTHLY_INSTALLMENT, APRIL).get("installment_number")).isEqualTo(1);

        cancelAsDirector(april, CreditNoteReason.BILLING_ERROR);
        issueInstallment(APRIL);

        List<Map<String, Object>> rows = discountsOn(FeeType.MONTHLY_INSTALLMENT, APRIL);
        assertThat(rows).hasSize(2).allSatisfy(row -> assertThat(row.get("student_discount_id")).isEqualTo(scholarship.getId()));
        assertThat(rows.get(1).get("installment_number")).isEqualTo(1);
        assertThat(rows.get(1).get("installments_total")).isEqualTo(3);
        assertThat(rows.get(0).get("consumption_released_at")).isNotNull();
        assertThat(rows.get(1).get("consumption_released_at")).isNull();
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void d06ACancellationWithoutReissueAlsoReturnsTheConsumptionOfTheDiscount() {
        Long promotionId = factory.acquireCohortPromotion(offering, learner, FeeType.MONTHLY_INSTALLMENT, Percentage.ofPoints(new BigDecimal("10")), 1);
        Long march = issueInstallment(MARCH);
        assertThat(statusOf(promotionId)).isEqualTo("EXHAUSTED");

        cancelAsDirector(march, CreditNoteReason.CANCELLATION);

        assertThat(onlyDiscountOn(FeeType.MONTHLY_INSTALLMENT, MARCH).get("consumption_released_at")).isNotNull();
        assertThat(statusOf(promotionId)).isEqualTo("ACTIVE");
        assertThat(liveUsesOf(promotionId)).isZero();
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-036")
    void d07AnIssuedInstallmentKeepsTheCalculationSnapshotWhenABetterScholarshipLaterWins() {
        Long tenPercent = factory.acquireCohortPromotion(offering, learner, FeeType.MONTHLY_INSTALLMENT, Percentage.ofPoints(new BigDecimal("10")), 3);
        issueInstallment(MARCH);
        today(LocalDate.of(2027, 3, 2));
        Long fifteenPercent = grantAsDirector(scholarship("15", APRIL, JUNE)).getId();
        issueInstallment(APRIL);

        Map<String, Object> march = onlyDiscountOn(FeeType.MONTHLY_INSTALLMENT, MARCH);
        Map<String, Object> april = onlyDiscountOn(FeeType.MONTHLY_INSTALLMENT, APRIL);
        assertThat(march.get("student_discount_id")).isEqualTo(tenPercent);
        assertThat(amount(march.get("discount_amount"))).isEqualByComparingTo("8000.00");
        assertThat(jdbc.queryForObject("select calculation::text from invoice_discount where id = (select d.id from invoice_discount d join invoice_line l on l.id = d.invoice_line_id "
                + "where l.program_enrollment_id = ? and l.period = date '2027-03-01')", String.class, learner.programEnrollmentId())).contains("10");
        assertThat(april.get("student_discount_id")).isEqualTo(fifteenPercent);
        assertThat(amount(april.get("discount_amount"))).isEqualByComparingTo("12000.00");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void d08AnInvoiceDiscountWithoutStudentDiscountOrWithAMissingOneIsRejectedByTheDatabase() {
        long line = insertDraftLine("d08");

        assertThatThrownBy(() -> insertDiscountRow(line, null, null, null)).isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("student_discount_id");
        assertThatThrownBy(() -> insertDiscountRow(line, Long.MAX_VALUE, null, null)).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_invoice_discount_student_discount");
    }

    @Test
    void d09TwoLiveDiscountsOfTheSameStudentDiscountAndInstallmentNumberAreRejectedByTheUniqueIndex() {
        StudentDiscount scholarship = grantAsDirector(scholarship("50", APRIL, JUNE));
        long first = insertDraftLine("d09a");
        long second = insertDraftLine("d09b");
        long released = insertDraftLine("d09c");

        insertDiscountRow(first, scholarship.getId(), 1, 3);
        assertThatThrownBy(() -> insertDiscountRow(second, scholarship.getId(), 1, 3)).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_invoice_discount_consumption");
        assertThat(insertDiscountRow(second, scholarship.getId(), 2, 3)).isPositive();
        jdbc.update("update invoice_discount set consumption_released_at = now() where invoice_line_id = ?", first);
        assertThat(insertDiscountRow(released, scholarship.getId(), 1, 3)).isPositive();
    }

    @Test
    void aCancellationRacingTheIssueOfTheNextInstallmentNeverDeadlocksAndLeavesConsistentConsumption() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 6; round++) {
                IssuingScenarioFactory.Learner racer = newPlainLearner();
                Long promotionId = factory.acquireCohortPromotion(offering, racer, FeeType.MONTHLY_INSTALLMENT, Percentage.ofPoints(new BigDecimal("10")), 3);
                today(LocalDate.of(2027, 3, 1));
                issue(installmentOf(racer, MARCH));
                today(LocalDate.of(2027, 4, 1));
                Long april = issue(installmentOf(racer, APRIL));
                today(LocalDate.of(2027, 5, 1));
                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch go = new CountDownLatch(1);
                Future<Boolean> cancelling = pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    cancelAsDirector(april, CreditNoteReason.BILLING_ERROR);
                    return true;
                });
                Future<Long> issuing = pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return issue(installmentOf(racer, MAY));
                });
                ready.await();
                go.countDown();
                cancelling.get(40, TimeUnit.SECONDS);
                issuing.get(40, TimeUnit.SECONDS);

                List<Integer> live = liveInstallmentNumbersOf(promotionId);
                assertThat(live).as("round %d", round).doesNotHaveDuplicates().hasSizeBetween(2, 3);
                assertThat(statusOf(promotionId)).as("round %d", round).isEqualTo(live.size() == 3 ? "EXHAUSTED" : "ACTIVE");
            }
            invariants.assertAllBillingInvariantsHold();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void d12AYearOfMonthlyIssuingWithScholarshipPromotionAndAReissueKeepsEveryInvariantEveryMonth() {
        Long promotionId = factory.acquireCohortPromotion(offering, learner, FeeType.MONTHLY_INSTALLMENT, Percentage.ofPoints(new BigDecimal("10")), 3);
        StudentDiscount scholarship = grantAsDirector(scholarship("50", JUNE, YearMonth.of(2027, 8)));
        Long june = null;
        for (int month = 3; month <= 12; month++) {
            YearMonth period = YearMonth.of(2027, month);
            Long invoice = issueInstallment(period);
            if (period.equals(JUNE)) {
                june = invoice;
            }
            if (month == 7) {
                cancelAsDirector(june, CreditNoteReason.BILLING_ERROR);
                issueInstallment(JUNE);
            }
            invariants.assertAllBillingInvariantsHold();
        }

        assertThat(liveInstallmentNumbersOf(promotionId)).containsExactly(1, 2, 3);
        assertThat(statusOf(promotionId)).isEqualTo("EXHAUSTED");
        assertThat(liveUsesOf(scholarship.getId())).isEqualTo(3L);
        assertThat(discountsOn(FeeType.MONTHLY_INSTALLMENT, JUNE)).hasSize(2);
        assertThat(discountsOn(FeeType.MONTHLY_INSTALLMENT, JUNE).get(1).get("installment_number")).isEqualTo(1);
    }

    private String statusOf(Long studentDiscountId) {
        return jdbc.queryForObject("select status from student_discount where id = ?", String.class, studentDiscountId);
    }

    private long liveUsesOf(Long studentDiscountId) {
        return jdbc.queryForObject("select count(*) from invoice_discount where student_discount_id = ? and consumption_released_at is null", Long.class, studentDiscountId);
    }

    private List<Integer> liveInstallmentNumbersOf(Long studentDiscountId) {
        return jdbc.queryForList("select installment_number from invoice_discount where student_discount_id = ? and consumption_released_at is null order by installment_number",
                Integer.class, studentDiscountId);
    }

    private long insertDraftLine(String key) {
        Long accountId = jdbc.queryForObject("""
                insert into student_current_account (student_id, currency, balance_amount, balance_amount_currency, balance_side,
                    unallocated_credit_amount, unallocated_credit_amount_currency, opened_at)
                values (?, 'ARS', 0, 'ARS', 'ZERO', 0, 'ARS', now()) returning id
                """, Long.class, newPlainLearner().studentId());
        Long invoiceId = jdbc.queryForObject("""
                insert into invoice (student_current_account_id, currency, document_type, invoice_type, issuing_reason, document_status,
                    lines_total, lines_total_currency, discounts_total, discounts_total_currency, total, total_currency, paid_amount, paid_amount_currency,
                    credited_amount, credited_amount_currency, outstanding_amount, outstanding_amount_currency, created_at, updated_at)
                values (?, 'ARS', 'INVOICE', 'MONTHLY_INSTALLMENT', 'MONTHLY_RUN', 'DRAFT', 100, 'ARS', 0, 'ARS', 100, 'ARS', 0, 'ARS', 0, 'ARS', 100, 'ARS', now(), now())
                returning id
                """, Long.class, accountId);
        return jdbc.queryForObject("""
                insert into invoice_line (invoice_id, currency, line_number, fee_type, concept, period, charge_key, amount, amount_currency, discount_amount,
                    discount_amount_currency, credited_amount, credited_amount_currency, free_credited_amount, free_credited_amount_currency)
                values (?, 'ARS', 1, 'ADMINISTRATIVE_FEE', 'Tasa', date '2027-03-01', ?, 100, 'ARS', 0, 'ARS', 0, 'ARS', 0, 'ARS') returning id
                """, Long.class, invoiceId, "K" + key + System.nanoTime());
    }

    private long insertDiscountRow(long lineId, Long studentDiscountId, Integer number, Integer total) {
        return jdbc.queryForObject("""
                insert into invoice_discount (invoice_line_id, student_discount_id, discount_type, concept, currency, base_amount, base_amount_currency, discount_amount,
                    discount_amount_currency, installment_number, installments_total)
                values (?, ?, 'SCHOLARSHIP', 'Beca', 'ARS', 100, 'ARS', 10, 'ARS', ?, ?) returning id
                """, Long.class, lineId, studentDiscountId, number, total);
    }
}
