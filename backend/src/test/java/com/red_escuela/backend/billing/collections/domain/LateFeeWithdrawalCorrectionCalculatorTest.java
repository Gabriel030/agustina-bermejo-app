package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.Percentage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-375")
class LateFeeWithdrawalCorrectionCalculatorTest {
    private static final LateFeeWithdrawalCorrectionCalculator CALCULATOR = new LateFeeWithdrawalCorrectionCalculator(new DailySimpleLateFeeCalculator());
    private static final Predicate<LocalDate> ALWAYS_ENABLED = day -> true;

    private static LocalDate april(int day) {
        return LocalDate.of(2027, 4, day);
    }

    private static LateFeeChargeSnapshot aprilCharge(String base, String rate, int chargedDays) {
        return new LateFeeChargeSnapshot(1L, 10L, new LateFeeAccrualPeriod(april(12), april(30)), chargedDays,
                NonNegativeMoney.of(new BigDecimal(base), Currency.ARS), Percentage.ofFraction(new BigDecimal(rate)));
    }

    private static Optional<PlannedLateFeeCorrection> plan(LateFeeChargeSnapshot charge, List<AppliedLateFeeCorrection> applied, LocalDate end, boolean frozen,
                                                          Predicate<LocalDate> enabled) {
        return CALCULATOR.planCorrectionOfDebitNote(charge, applied, end, frozen, enabled);
    }

    @Test
    @Tag("D-330")
    @Tag("D-360")
    void anEndInTheMiddleOfThePeriodCorrectsTheDaysAfterIt() {
        Optional<PlannedLateFeeCorrection> planned = plan(aprilCharge("100000.00", "0.002", 19), List.of(), april(28), true, ALWAYS_ENABLED);

        assertThat(planned).isPresent();
        assertThat(planned.get().correctedRange()).isEqualTo(new LateFeeAccrualPeriod(april(29), april(30)));
        assertThat(planned.get().correctedDays()).isEqualTo(2);
        assertThat(planned.get().creditAmount().value().amount()).isEqualByComparingTo("400.00");
    }

    @Test
    void anEndOnTheLastDayOfThePeriodOrLaterCorrectsNothing() {
        assertThat(plan(aprilCharge("100000.00", "0.002", 19), List.of(), april(30), true, ALWAYS_ENABLED)).isEmpty();
        assertThat(plan(aprilCharge("100000.00", "0.002", 19), List.of(), LocalDate.of(2027, 5, 1), true, ALWAYS_ENABLED)).isEmpty();
    }

    @Test
    void anEndBeforeThePeriodCorrectsAllTheChargedDays() {
        Optional<PlannedLateFeeCorrection> planned = plan(aprilCharge("100000.00", "0.002", 19), List.of(), april(11), true, ALWAYS_ENABLED);

        assertThat(planned.get().correctedDays()).isEqualTo(19);
        assertThat(planned.get().correctedRange()).isEqualTo(new LateFeeAccrualPeriod(april(12), april(30)));
        assertThat(planned.get().creditAmount().value().amount()).isEqualByComparingTo("3800.00");
    }

    @Test
    @Tag("D-148")
    @Tag("D-360")
    void aRuleThatDoesNotFreezeOnTheEndDateCorrectsNothing() {
        assertThat(plan(aprilCharge("100000.00", "0.002", 19), List.of(), april(11), false, ALWAYS_ENABLED)).isEmpty();
    }

    @Test
    @Tag("D-330")
    void aDayWithChargingDisabledIsNeverCorrectedBecauseItWasNeverCharged() {
        Predicate<LocalDate> disabledOnTwentyNinth = day -> !day.equals(april(29));

        Optional<PlannedLateFeeCorrection> planned = plan(aprilCharge("100000.00", "0.002", 18), List.of(), april(28), true, disabledOnTwentyNinth);

        assertThat(planned.get().correctedDays()).isEqualTo(1);
        assertThat(planned.get().correctedRange()).isEqualTo(new LateFeeAccrualPeriod(april(30), april(30)));
        assertThat(planned.get().creditAmount().value().amount()).isEqualByComparingTo("200.00");
    }

    @Test
    void aPreviouslyCorrectedDayIsNotCorrectedTwice() {
        List<AppliedLateFeeCorrection> applied = List.of(new AppliedLateFeeCorrection(new LateFeeAccrualPeriod(april(30), april(30)), 1));

        Optional<PlannedLateFeeCorrection> planned = plan(aprilCharge("100000.00", "0.002", 19), applied, april(28), true, ALWAYS_ENABLED);

        assertThat(planned.get().correctedDays()).isEqualTo(1);
        assertThat(planned.get().correctedRange()).isEqualTo(new LateFeeAccrualPeriod(april(29), april(29)));
        assertThat(planned.get().creditAmount().value().amount()).isEqualByComparingTo("200.00");
    }

    @Test
    void aFullyCorrectedDebitNoteHasNothingLeftToCorrect() {
        List<AppliedLateFeeCorrection> applied = List.of(new AppliedLateFeeCorrection(new LateFeeAccrualPeriod(april(12), april(30)), 19));

        assertThat(plan(aprilCharge("100000.00", "0.002", 19), applied, april(11), true, ALWAYS_ENABLED)).isEmpty();
    }

    @Test
    @Tag("D-099")
    void theCreditIsTheComplementOfTheRoundedTotalAndNotTheRoundedProductOfTheCorrectedDays() {
        LateFeeChargeSnapshot charge = aprilCharge("80333.33", "0.0017", 19);

        Optional<PlannedLateFeeCorrection> planned = plan(charge, List.of(), april(28), true, ALWAYS_ENABLED);

        NonNegativeMoney total = new DailySimpleLateFeeCalculator().calculateLateFee(charge.baseAmount(), charge.dailyRate(), 19);
        NonNegativeMoney remaining = new DailySimpleLateFeeCalculator().calculateLateFee(charge.baseAmount(), charge.dailyRate(), 17);
        assertThat(planned.get().creditAmount().value().amount()).isEqualByComparingTo("273.14");
        assertThat(new DailySimpleLateFeeCalculator().calculateLateFee(charge.baseAmount(), charge.dailyRate(), 2).value().amount()).isEqualByComparingTo("273.13");
        assertThat(total.value().amount().subtract(planned.get().creditAmount().value().amount())).isEqualByComparingTo(remaining.value().amount());
    }

    @Test
    void daysThatExceedWhatWasChargedAreLimitedToTheChargedDays() {
        Predicate<LocalDate> allEnabledAfterTheEnd = day -> true;

        Optional<PlannedLateFeeCorrection> planned = plan(aprilCharge("100000.00", "0.002", 1), List.of(), april(28), true, allEnabledAfterTheEnd);

        assertThat(planned.get().correctedDays()).isEqualTo(1);
        assertThat(planned.get().correctedRange()).isEqualTo(new LateFeeAccrualPeriod(april(29), april(29)));
        assertThat(planned.get().creditAmount().value().amount()).isEqualByComparingTo("200.00");
    }

    @Test
    void whenMoreDaysWereAlreadyCorrectedThanChargedNothingIsPlanned() {
        List<AppliedLateFeeCorrection> applied = List.of(new AppliedLateFeeCorrection(new LateFeeAccrualPeriod(april(12), april(20)), 9));

        assertThat(plan(aprilCharge("100000.00", "0.002", 5), applied, april(28), true, ALWAYS_ENABLED)).isEmpty();
    }

    @Test
    void whenNoDayAfterTheEndHadChargingEnabledNothingIsPlanned() {
        assertThat(plan(aprilCharge("100000.00", "0.002", 10), List.of(), april(28), true, day -> day.isBefore(april(29)))).isEmpty();
    }

    @Test
    void aCorrectionPlannedWithMoreDaysThanItsRangeIsRejected() {
        assertThatThrownBy(() -> new PlannedLateFeeCorrection(new LateFeeAccrualPeriod(april(29), april(30)), 3, NonNegativeMoney.zero(Currency.ARS)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
