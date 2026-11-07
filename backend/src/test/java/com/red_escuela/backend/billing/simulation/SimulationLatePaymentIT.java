package com.red_escuela.backend.billing.simulation;

import com.red_escuela.backend.testsupport.ExplorationTier;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static com.red_escuela.backend.billing.simulation.Ledger.Row;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SimulationTest
class SimulationLatePaymentIT extends AbstractSimulationTest {

    private static final YearMonth MARCH = YearMonth.of(2027, 3);
    private static final YearMonth APRIL = YearMonth.of(2027, 4);
    private static final YearMonth MAY = YearMonth.of(2027, 5);

    private static void onlyInExplorationTierNightly() {
        assumeTrue(ExplorationTier.current() == ExplorationTier.NIGHTLY, "late payment simulation runs only in the nightly exploration tier");
    }

    private static LocalDate day(int month, int dayOfMonth) {
        return LocalDate.of(2027, month, dayOfMonth);
    }

    private static Row enrollmentFee() {
        return Row.of("ENROLLMENT_FEE", MARCH, day(3, 1), day(3, 1), "60000.00", "0.00", "ISSUED", "PAID");
    }

    private static Row installment(YearMonth month, String outstanding, String paymentStatus) {
        return Row.of("MONTHLY_INSTALLMENT", month, month.atDay(1), month.atDay(11), "48000.00", outstanding, "ISSUED", paymentStatus);
    }

    private static Row installment(YearMonth month, LocalDate issued, LocalDate due, String outstanding, String paymentStatus) {
        return Row.of("MONTHLY_INSTALLMENT", month, issued, due, "48000.00", outstanding, "ISSUED", paymentStatus);
    }

    private static Row lateFee(YearMonth origin, LocalDate issued, LocalDate due, String total, String outstanding, String paymentStatus) {
        return Row.of("LATE_FEE", origin, issued, due, total, outstanding, "ISSUED", paymentStatus);
    }

    private Map<String, Object> standing(Scenario scenario) {
        return jdbcTemplate.queryForMap("select status, suspended_since from enrollment_payment_standing where student_id = ?", scenario.studentId());
    }

    private String standingStatus(Scenario scenario) {
        return (String) standing(scenario).get("status");
    }

    private Scenario paidMarch(Timeline.Mode mode) {
        Scenario scenario = scenario(mode, 0, 1);
        scenario.enrollStudent(day(3, 1));
        scenario.advanceTo(day(3, 5)).payEverythingDue();
        return scenario;
    }

    @Test
    @Tag("D-146")
    @Tag("D-288")
    void installmentBecomesOverdueTheDayAfterDueDatePlusRuleOfTheDay() {
        onlyInExplorationTierNightly();
        Scenario scenario = paidMarch(Timeline.Mode.DAILY);

        for (LocalDate current = day(4, 8); !current.isAfter(day(4, 18)); current = current.plusDays(1)) {
            scenario.advanceTo(current);
            String expected = current.isBefore(day(4, 17)) ? "PENDING" : "OVERDUE";
            assertThat(scenario.ledger().rows().get(2).paymentStatus()).as("April installment on %s", current).isEqualTo(expected);
        }
        scenario.finish();
    }

    @Test
    @Tag("D-330")
    @Tag("D-331")
    @Tag("D-297")
    void lateFeeIsDailyFromOriginalDueDateWhenPaidAfterGrace() {
        onlyInExplorationTierNightly();
        Scenario scenario = paidMarch(Timeline.Mode.DAILY);

        scenario.advanceTo(day(4, 20)).payEverythingDue();

        scenario.ledger().hasExactly(enrollmentFee(), installment(MARCH, "0.00", "PAID"), installment(APRIL, "0.00", "PAID"),
                lateFee(APRIL, day(4, 20), day(4, 30), "734.40", "734.40", "PENDING"));
        scenario.finish();
    }

    @Test
    @Tag("D-330")
    @Tag("D-331")
    void paidTheDayAfterGraceChargesAllDaysSinceDueDate() {
        onlyInExplorationTierNightly();
        Scenario scenario = paidMarch(Timeline.Mode.DAILY);

        scenario.advanceTo(day(4, 17)).payEverythingDue();

        scenario.ledger().hasExactly(enrollmentFee(), installment(MARCH, "0.00", "PAID"), installment(APRIL, "0.00", "PAID"),
                lateFee(APRIL, day(4, 17), day(4, 27), "489.60", "489.60", "PENDING"));
        scenario.finish();
    }

    @Test
    @Tag("D-331")
    void noLateFeeWhenPaidInsideGrace() {
        onlyInExplorationTierNightly();
        Scenario scenario = paidMarch(Timeline.Mode.DAILY);

        scenario.advanceTo(day(4, 16)).payEverythingDue();
        scenario.advanceTo(day(5, 3));

        scenario.ledger().hasExactly(enrollmentFee(), installment(MARCH, "0.00", "PAID"), installment(APRIL, "0.00", "PAID"),
                installment(MAY, "48000.00", "PENDING"));
        scenario.finish();
    }

    @Test
    @Tag("D-332")
    void monthEndClosingEmitsOneDebitNotePerOriginAndMonth() {
        onlyInExplorationTierNightly();
        Scenario scenario = paidMarch(Timeline.Mode.DAILY);

        scenario.advanceTo(day(4, 30));
        scenario.ledger().hasExactly(enrollmentFee(), installment(MARCH, "0.00", "PAID"), installment(APRIL, "48000.00", "OVERDUE"));
        scenario.advanceTo(day(5, 3));

        scenario.ledger().hasExactly(enrollmentFee(), installment(MARCH, "0.00", "PAID"), installment(APRIL, "48000.00", "OVERDUE"),
                lateFee(APRIL, day(5, 1), day(5, 11), "1550.40", "1550.40", "PENDING"));
        scenario.finish();
    }

    @Test
    @Tag("D-332")
    @Tag("D-379")
    void latePaymentAfterClosingNoteEmitsFinalNoteForRemainingDays() {
        onlyInExplorationTierNightly();
        Scenario scenario = paidMarch(Timeline.Mode.DAILY);

        scenario.advanceTo(day(5, 3)).payEverythingDue();

        scenario.ledger().hasExactly(enrollmentFee(), installment(MARCH, "0.00", "PAID"), installment(APRIL, "0.00", "PAID"),
                lateFee(APRIL, day(5, 1), day(5, 11), "1550.40", "0.00", "PAID"), lateFee(APRIL, day(5, 3), day(5, 13), "244.80", "244.80", "PENDING"),
                installment(MAY, day(5, 3), day(5, 13), "48000.00", "PENDING"));
        scenario.finish();
    }

    @Test
    @Tag("D-289")
    @Tag("D-149")
    void lateFeeIsNeverChargedOnDebitNotes() {
        onlyInExplorationTierNightly();
        Scenario scenario = paidMarch(Timeline.Mode.DAILY);

        scenario.advanceTo(day(5, 3)).payEverythingDue();
        scenario.advanceTo(day(6, 2));

        scenario.ledger().hasExactly(enrollmentFee(), installment(MARCH, "0.00", "PAID"), installment(APRIL, "0.00", "PAID"),
                lateFee(APRIL, day(5, 1), day(5, 11), "1550.40", "0.00", "PAID"), lateFee(APRIL, day(5, 3), day(5, 13), "244.80", "244.80", "OVERDUE"),
                installment(MAY, day(5, 3), day(5, 13), "48000.00", "OVERDUE"), lateFee(MAY, day(6, 1), day(6, 11), "1468.80", "1468.80", "PENDING"));
        scenario.finish();
    }

    @Test
    @Tag("D-241")
    @Tag("D-306")
    @Tag("D-472")
    @Tag("D-473")
    void unpaidInstallmentAtMonthEndSuspendsByDefaultThreshold() {
        onlyInExplorationTierNightly();
        Scenario scenario = paidMarch(Timeline.Mode.DAILY);

        scenario.advanceTo(day(4, 30));
        assertThat(standingStatus(scenario)).isEqualTo("OVERDUE");
        scenario.advanceTo(day(5, 1));
        assertThat(standing(scenario)).containsEntry("status", "SUSPENDED").containsEntry("suspended_since", java.sql.Date.valueOf(day(5, 1)));

        scenario.advanceTo(day(6, 2));
        scenario.ledger().hasExactly(enrollmentFee(), installment(MARCH, "0.00", "PAID"), installment(APRIL, "48000.00", "OVERDUE"),
                lateFee(APRIL, day(5, 1), day(5, 11), "1550.40", "1550.40", "OVERDUE"), lateFee(APRIL, day(6, 1), day(6, 11), "2529.60", "2529.60", "PENDING"));
        scenario.finish();
    }

    @Test
    @Tag("D-066")
    @Tag("D-241")
    @Tag("D-292")
    void payingWhatWasDueAtSuspensionLiftsIt() {
        onlyInExplorationTierNightly();
        Scenario scenario = paidMarch(Timeline.Mode.DAILY);
        scenario.advanceTo(day(5, 2));
        assertThat(standingStatus(scenario)).isEqualTo("SUSPENDED");

        scenario.advanceTo(day(5, 3)).payEverythingDue();

        assertThat(standingStatus(scenario)).isEqualTo("UP_TO_DATE");
        assertThat(scenario.ledger().rows()).extracting(Row::type, Row::period, Row::issueDate).containsSubsequence(
                org.assertj.core.groups.Tuple.tuple("MONTHLY_INSTALLMENT", MAY, day(5, 3)));
        scenario.advanceTo(day(5, 10)).payEverythingDue();
        scenario.advanceTo(day(6, 2));
        assertThat(scenario.ledger().rows()).extracting(Row::type, Row::period, Row::issueDate).contains(
                org.assertj.core.groups.Tuple.tuple("MONTHLY_INSTALLMENT", YearMonth.of(2027, 6), day(6, 1)));
        scenario.finish();
    }

    @Test
    @Tag("D-139")
    @Tag("D-136")
    void insufficientPaymentStaysAsUnallocatedCredit() {
        onlyInExplorationTierNightly();
        Scenario scenario = paidMarch(Timeline.Mode.DAILY);
        scenario.advanceTo(day(4, 20));

        scenario.payAmount("40000.00");

        scenario.ledger().hasExactly(enrollmentFee(), installment(MARCH, "0.00", "PAID"), installment(APRIL, "48000.00", "OVERDUE"));
        List<java.math.BigDecimal> credit = jdbcTemplate.queryForList("select unallocated_credit_amount from student_current_account where student_id = ?", java.math.BigDecimal.class,
                scenario.studentId());
        assertThat(credit).singleElement().satisfies(value -> assertThat(value).isEqualByComparingTo("40000.00"));
        scenario.finish();
    }

    @Test
    @Tag("D-066")
    @Tag("D-241")
    @Tag("D-330")
    @Tag("D-332")
    void latePayerSuspendedPaysAndContinuesForTheWholeYear() {
        Scenario scenario = paidMarch(Timeline.Mode.SPARSE);

        scenario.advanceTo(day(5, 1));
        assertThat(standingStatus(scenario)).isEqualTo("SUSPENDED");
        scenario.advanceTo(day(5, 3)).payEverythingDue();
        scenario.advanceTo(day(5, 10)).payEverythingDue();
        for (int month = 6; month <= 12; month++) {
            scenario.advanceTo(day(month, 5)).payEverythingDue();
        }
        scenario.advanceTo(day(12, 31)).finish();

        Ledger ledger = scenario.ledger();
        assertThat(ledger.outstandingTotal()).isEqualByComparingTo("0");
        ledger.hasExactly(enrollmentFee(), installment(MARCH, "0.00", "PAID"), installment(APRIL, "0.00", "PAID"),
                lateFee(APRIL, day(5, 1), day(5, 11), "1550.40", "0.00", "PAID"), lateFee(APRIL, day(5, 3), day(5, 13), "244.80", "0.00", "PAID"),
                installment(MAY, day(5, 3), day(5, 13), "0.00", "PAID"), installment(YearMonth.of(2027, 6), "0.00", "PAID"), installment(YearMonth.of(2027, 7), "0.00", "PAID"),
                installment(YearMonth.of(2027, 8), "0.00", "PAID"), installment(YearMonth.of(2027, 9), "0.00", "PAID"), installment(YearMonth.of(2027, 10), "0.00", "PAID"),
                installment(YearMonth.of(2027, 11), "0.00", "PAID"), installment(YearMonth.of(2027, 12), "0.00", "PAID"));
    }

    @Test
    @Tag("D-332")
    @Tag("D-449")
    void nightRunTwiceOnTheSameDayDoesNotDuplicateLateFees() {
        onlyInExplorationTierNightly();
        Scenario scenario = paidMarch(Timeline.Mode.DAILY);
        scenario.advanceTo(day(5, 1));
        Ledger once = scenario.ledger();

        scenario.timeline().jobs().stream().filter(job -> job.name().equals("NightlyBillingJob")).forEach(job -> job.action().run());

        assertThat(scenario.ledger().render()).isEqualTo(once.render());
        scenario.finish();
    }
}
