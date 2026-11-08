package com.red_escuela.backend.billing.simulation;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.support.PriceFixtures;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.testsupport.ExplorationTier;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static com.red_escuela.backend.billing.simulation.Ledger.Row;
import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SimulationTest
class SimulationDiscountsIT extends AbstractSimulationTest {

    private static final YearMonth MARCH = YearMonth.of(2027, 3);
    private static final YearMonth APRIL = YearMonth.of(2027, 4);
    private static final YearMonth MAY = YearMonth.of(2027, 5);
    private static final YearMonth JUNE = YearMonth.of(2027, 6);
    private static final YearMonth JULY = YearMonth.of(2027, 7);

    private static void onlyInExplorationTierNightly() {
        assumeTrue(ExplorationTier.current() == ExplorationTier.NIGHTLY, "full discount simulation runs only in the nightly exploration tier");
    }

    private static Row enrollmentFee(String total, String outstanding, String paymentStatus) {
        return Row.of("ENROLLMENT_FEE", MARCH, LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 1), total, outstanding, "ISSUED", paymentStatus);
    }

    private static Row installment(YearMonth month, String total, String outstanding, String paymentStatus) {
        return Row.of("MONTHLY_INSTALLMENT", month, month.atDay(1), month.atDay(11), total, outstanding, "ISSUED", paymentStatus);
    }

    private long creditNotes() {
        return jdbcTemplate.queryForObject("select count(*) from credit_note", Long.class);
    }

    @Test
    @Tag("D-238")
    @Tag("D-284")
    void scholarshipAppliesOnlyFromNextInstallmentToIssue() {
        Scenario scenario = scenario(Timeline.Mode.SPARSE);
        DiscountActions discounts = discounts(scenario);
        long creditNotesBefore = creditNotes();

        scenario.enrollStudent(LocalDate.of(2027, 3, 1));
        scenario.advanceTo(LocalDate.of(2027, 3, 5)).payEverythingDue();
        scenario.advanceTo(LocalDate.of(2027, 4, 5)).payEverythingDue();
        scenario.advanceTo(LocalDate.of(2027, 4, 15));
        discounts.grantScholarship("50", MAY, JULY);
        scenario.advanceTo(LocalDate.of(2027, 5, 5)).payEverythingDue();
        scenario.advanceTo(LocalDate.of(2027, 6, 1)).finish();

        scenario.ledger().hasExactly(enrollmentFee("60000.00", "0.00", "PAID"), installment(MARCH, "48000.00", "0.00", "PAID"),
                installment(APRIL, "48000.00", "0.00", "PAID"), installment(MAY, "24000.00", "0.00", "PAID"), installment(JUNE, "24000.00", "24000.00", "PENDING"));
        assertThat(creditNotes()).isEqualTo(creditNotesBefore);
    }

    @Test
    @Tag("D-150")
    @Tag("D-193")
    void hundredPercentScholarshipEmitsZeroInvoicePaidAutomatically() {
        onlyInExplorationTierNightly();
        Scenario scenario = scenario(Timeline.Mode.SPARSE);
        DiscountActions discounts = discounts(scenario);

        scenario.enrollStudent(LocalDate.of(2027, 3, 1));
        scenario.advanceTo(LocalDate.of(2027, 3, 5)).payEverythingDue();
        discounts.grantScholarship("100", APRIL, APRIL);
        scenario.advanceTo(LocalDate.of(2027, 5, 1)).finish();

        scenario.ledger().hasExactly(enrollmentFee("60000.00", "0.00", "PAID"), installment(MARCH, "48000.00", "0.00", "PAID"),
                installment(APRIL, "0.00", "0.00", "PAID"), installment(MAY, "48000.00", "48000.00", "PENDING"));
    }

    private void payOnTheFifthOf(Scenario scenario, YearMonth... months) {
        for (YearMonth month : months) {
            scenario.advanceTo(month.atDay(5)).payEverythingDue();
        }
    }

    private static Row septemberEnrollmentFee() {
        return Row.of("ENROLLMENT_FEE", MARCH, LocalDate.of(2027, 9, 1), LocalDate.of(2027, 9, 11), "60000.00", "0.00", "ISSUED", "PAID");
    }

    private Row paidInstallment(YearMonth month, String total) {
        return installment(month, total, "0.00", "PAID");
    }

    @Test
    @Tag("D-129")
    void discountOnlyAppliesToItsInvoiceType() {
        onlyInExplorationTierNightly();
        Scenario scenario = scenario(Timeline.Mode.SPARSE);
        DiscountActions discounts = discounts(scenario);

        scenario.enrollStudent(LocalDate.of(2027, 3, 1));
        scenario.advanceTo(LocalDate.of(2027, 3, 5)).payEverythingDue();
        discounts.grantIndividual(InvoiceType.MONTHLY_INSTALLMENT, "25", APRIL, APRIL);
        payOnTheFifthOf(scenario, APRIL, MAY);
        scenario.advanceTo(LocalDate.of(2027, 5, 6)).finish();

        scenario.ledger().hasExactly(enrollmentFee("60000.00", "0.00", "PAID"), paidInstallment(MARCH, "48000.00"), paidInstallment(APRIL, "36000.00"),
                paidInstallment(MAY, "48000.00"));
    }

    @Test
    @Tag("D-282")
    void scholarshipRangeLosingMonthIsLostWithoutShifting() {
        onlyInExplorationTierNightly();
        Scenario scenario = scenario(Timeline.Mode.SPARSE);
        DiscountActions discounts = discounts(scenario);

        scenario.enrollStudent(LocalDate.of(2027, 3, 1));
        discounts.grantScholarship("50", APRIL, JULY);
        discounts.grantIndividual(InvoiceType.MONTHLY_INSTALLMENT, "60", MAY, MAY);
        payOnTheFifthOf(scenario, MARCH, APRIL, MAY, JUNE, JULY);
        scenario.advanceTo(LocalDate.of(2027, 8, 6)).finish();

        scenario.ledger().hasExactly(enrollmentFee("60000.00", "0.00", "PAID"), paidInstallment(MARCH, "48000.00"), paidInstallment(APRIL, "24000.00"),
                paidInstallment(MAY, "19200.00"), paidInstallment(JUNE, "24000.00"), paidInstallment(JULY, "24000.00"), installment(YearMonth.of(2027, 8), "48000.00", "48000.00", "PENDING"));
    }

    @Test
    @Tag("D-295")
    void mostFavorableDiscountWinsPerLine() {
        onlyInExplorationTierNightly();
        Scenario scenario = scenario(Timeline.Mode.SPARSE);
        DiscountActions discounts = discounts(scenario);

        scenario.enrollStudent(LocalDate.of(2027, 3, 1));
        discounts.grantScholarship("30", APRIL, APRIL);
        discounts.grantIndividual(InvoiceType.MONTHLY_INSTALLMENT, "40", APRIL, APRIL);
        payOnTheFifthOf(scenario, MARCH, APRIL);
        scenario.advanceTo(LocalDate.of(2027, 4, 6)).finish();

        scenario.ledger().hasExactly(enrollmentFee("60000.00", "0.00", "PAID"), paidInstallment(MARCH, "48000.00"), paidInstallment(APRIL, "28800.00"));
    }

    @Test
    @Tag("D-297")
    @Tag("D-151")
    @Tag("D-331")
    void lateFeeIsComputedOnFinalPriceNotOnListPrice() {
        onlyInExplorationTierNightly();
        Scenario scenario = scenario(Timeline.Mode.SPARSE);
        DiscountActions discounts = discounts(scenario);
        scenario.timeline().advanceTo(LocalDate.of(2027, 2, 20));
        PriceFixtures.persistPriceVersion(scenario.programId(), null, FeeType.MONTHLY_INSTALLMENT, "Cuota", ars(48000), APRIL.atDay(1), scenario.directorId(), "PERCENTAGE",
                new java.math.BigDecimal("25"));

        scenario.enrollStudent(LocalDate.of(2027, 3, 1));
        scenario.advanceTo(LocalDate.of(2027, 3, 5)).payEverythingDue();
        discounts.grantScholarship("50", APRIL, APRIL);
        scenario.advanceTo(LocalDate.of(2027, 5, 2)).finish();

        scenario.ledger().hasExactly(enrollmentFee("60000.00", "0.00", "PAID"), paidInstallment(MARCH, "48000.00"), installment(APRIL, "18000.00", "18000.00", "OVERDUE"),
                Row.of("LATE_FEE", APRIL, LocalDate.of(2027, 5, 1), LocalDate.of(2027, 5, 11), "1162.80", "1162.80", "ISSUED", "PENDING"),
                installment(MAY, "36000.00", "36000.00", "PENDING"));
    }

    @Test
    @Tag("D-238")
    @Tag("D-284")
    void scholarshipRevokedOnlyForwardFromNextInstallment() {
        onlyInExplorationTierNightly();
        Scenario scenario = scenario(Timeline.Mode.SPARSE);
        DiscountActions discounts = discounts(scenario);
        long creditNotesBefore = creditNotes();

        scenario.enrollStudent(LocalDate.of(2027, 3, 1));
        Long scholarship = discounts.grantScholarship("50", APRIL, YearMonth.of(2027, 12));
        payOnTheFifthOf(scenario, MARCH, APRIL, MAY, JUNE);
        scenario.advanceTo(LocalDate.of(2027, 6, 10));
        discounts.revoke(scholarship, "Cambio la situacion economica");
        payOnTheFifthOf(scenario, JULY);
        scenario.advanceTo(LocalDate.of(2027, 7, 6)).finish();

        scenario.ledger().hasExactly(enrollmentFee("60000.00", "0.00", "PAID"), paidInstallment(MARCH, "48000.00"), paidInstallment(APRIL, "24000.00"),
                paidInstallment(MAY, "24000.00"), paidInstallment(JUNE, "24000.00"), paidInstallment(JULY, "48000.00"));
        assertThat(creditNotes()).isEqualTo(creditNotesBefore);
    }

    private void persistPriceFrom(Scenario scenario, LocalDate effectiveFrom, String amount) {
        PriceFixtures.persistPriceVersion(scenario.programId(), null, FeeType.MONTHLY_INSTALLMENT, "Cuota", Money.of(new java.math.BigDecimal(amount),
                com.red_escuela.backend.shared.domain.Currency.ARS), effectiveFrom, scenario.directorId());
    }

    @Test
    @Tag("D-035")
    @Tag("D-161")
    @Tag("D-054")
    void priceIncreaseOnDayThreeDoesNotChangeInstallmentsAlreadyIssuedOnDayOne() {
        onlyInExplorationTierNightly();
        Scenario scenario = scenario(Timeline.Mode.SPARSE);
        YearMonth october = YearMonth.of(2027, 10);
        YearMonth november = YearMonth.of(2027, 11);

        scenario.enrollStudent(LocalDate.of(2027, 9, 1));
        payOnTheFifthOf(scenario, YearMonth.of(2027, 9));
        scenario.advanceTo(LocalDate.of(2027, 10, 3));
        persistPriceFrom(scenario, LocalDate.of(2027, 10, 3), "52800");
        payOnTheFifthOf(scenario, october);
        scenario.advanceTo(LocalDate.of(2027, 11, 2)).finish();

        scenario.ledger().hasExactly(septemberEnrollmentFee(),
                paidInstallment(YearMonth.of(2027, 9), "48000.00"), paidInstallment(october, "48000.00"), installment(november, "52800.00", "52800.00", "PENDING"));
    }

    @Test
    @Tag("D-161")
    @Tag("D-035")
    void priceChangeScheduledBeforeTheRunOfTheSameDayAppliesToThatMonth() {
        onlyInExplorationTierNightly();
        Scenario scenario = scenario(Timeline.Mode.SPARSE);
        YearMonth october = YearMonth.of(2027, 10);

        scenario.enrollStudent(LocalDate.of(2027, 9, 1));
        payOnTheFifthOf(scenario, YearMonth.of(2027, 9));
        scenario.advanceTo(LocalDate.of(2027, 9, 30));
        persistPriceFrom(scenario, LocalDate.of(2027, 10, 1), "52800");
        scenario.advanceTo(LocalDate.of(2027, 10, 2)).finish();

        scenario.ledger().hasExactly(septemberEnrollmentFee(),
                paidInstallment(YearMonth.of(2027, 9), "48000.00"), installment(october, "52800.00", "52800.00", "PENDING"));
    }

    @Test
    @Tag("D-161")
    @Tag("D-035")
    void priceChangeRegisteredAfterTheRunOfTheSameDayDoesNotReachThatMonth() {
        onlyInExplorationTierNightly();
        Scenario scenario = scenario(Timeline.Mode.SPARSE);
        YearMonth october = YearMonth.of(2027, 10);

        scenario.enrollStudent(LocalDate.of(2027, 9, 1));
        payOnTheFifthOf(scenario, YearMonth.of(2027, 9));
        scenario.advanceTo(LocalDate.of(2027, 10, 1));
        persistPriceFrom(scenario, LocalDate.of(2027, 10, 1), "52800");
        scenario.advanceTo(LocalDate.of(2027, 10, 2)).finish();

        scenario.ledger().hasExactly(septemberEnrollmentFee(),
                paidInstallment(YearMonth.of(2027, 9), "48000.00"), installment(october, "48000.00", "48000.00", "PENDING"));
    }
}
