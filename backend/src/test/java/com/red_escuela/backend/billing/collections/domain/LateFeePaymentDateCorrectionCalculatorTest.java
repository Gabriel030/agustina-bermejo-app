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

@Tag("D-378")
@Tag("D-331")
class LateFeePaymentDateCorrectionCalculatorTest {
    private static final LateFeePaymentDateCorrectionCalculator CALCULATOR = new LateFeePaymentDateCorrectionCalculator(new DailySimpleLateFeeCalculator());
    private static final Predicate<LocalDate> EVERY_DAY = day -> true;

    private static LocalDate march(int day) {
        return LocalDate.of(2027, 3, day);
    }

    private static LateFeeChargeSnapshot marchCharge(String base, String rate, int chargedDays) {
        return new LateFeeChargeSnapshot(1L, 10L, new LateFeeAccrualPeriod(march(12), march(31)), chargedDays,
                NonNegativeMoney.of(new BigDecimal(base), Currency.ARS), Percentage.ofFraction(new BigDecimal(rate)));
    }

    private static Optional<PlannedLateFeeCorrection> plan(LateFeeChargeSnapshot charge, List<AppliedLateFeeCorrection> applied, LocalDate paymentDate,
                                                          boolean withinGrace, Predicate<LocalDate> chargedOn) {
        return CALCULATOR.planCorrectionOfDebitNote(charge, applied, paymentDate, withinGrace, chargedOn);
    }

    private static AppliedLateFeeCorrection applied(int firstDay, int lastDay) {
        return new AppliedLateFeeCorrection(new LateFeeAccrualPeriod(march(firstDay), march(lastDay)), lastDay - firstDay + 1);
    }

    @Test
    void aPaymentInTheMiddleOfThePeriodCorrectsOnlyTheDaysAfterItAndKeepsThePaymentDay() {
        Optional<PlannedLateFeeCorrection> planned = plan(marchCharge("100000.00", "0.002", 20), List.of(), march(28), false, EVERY_DAY);

        assertThat(planned).isPresent();
        assertThat(planned.get().correctedRange()).isEqualTo(new LateFeeAccrualPeriod(march(29), march(31)));
        assertThat(planned.get().correctedDays()).isEqualTo(3);
        assertThat(planned.get().creditAmount().value().amount()).isEqualByComparingTo("600.00");
        assertThat(plan(marchCharge("100000.00", "0.002", 20), List.of(), march(20), false, EVERY_DAY).get().correctedDays()).isEqualTo(11);
        assertThat(plan(marchCharge("100000.00", "0.002", 20), List.of(), march(30), false, EVERY_DAY).get().correctedRange())
                .isEqualTo(new LateFeeAccrualPeriod(march(31), march(31)));
    }

    @Test
    void aPaymentOnTheLastDayOfThePeriodOrLaterCorrectsNothing() {
        assertThat(plan(marchCharge("100000.00", "0.002", 20), List.of(), march(31), false, EVERY_DAY)).isEmpty();
        assertThat(plan(marchCharge("100000.00", "0.002", 20), List.of(), LocalDate.of(2027, 4, 2), false, EVERY_DAY)).isEmpty();
    }

    @Test
    void aPaymentBeforeThePeriodCorrectsAllTheChargedDays() {
        Optional<PlannedLateFeeCorrection> planned = plan(marchCharge("100000.00", "0.002", 20), List.of(), march(10), false, EVERY_DAY);

        assertThat(planned.get().correctedRange()).isEqualTo(new LateFeeAccrualPeriod(march(12), march(31)));
        assertThat(planned.get().correctedDays()).isEqualTo(20);
        assertThat(planned.get().creditAmount().value().amount()).isEqualByComparingTo("4000.00");
    }

    @Test
    void aPaymentInsideTheGraceCorrectsEveryChargedDayWhateverItsDateIs() {
        Optional<PlannedLateFeeCorrection> planned = plan(marchCharge("100000.00", "0.002", 20), List.of(), march(20), true, EVERY_DAY);

        assertThat(planned.get().correctedRange()).isEqualTo(new LateFeeAccrualPeriod(march(12), march(31)));
        assertThat(planned.get().correctedDays()).isEqualTo(20);
        assertThat(planned.get().creditAmount().value().amount()).isEqualByComparingTo("4000.00");
        assertThat(plan(marchCharge("100000.00", "0.002", 20), List.of(), march(31), true, EVERY_DAY).get().correctedDays()).isEqualTo(20);
    }

    @Test
    @Tag("D-380")
    void aDayThatWasNotChargedIsNeverCorrected() {
        Predicate<LocalDate> notChargedOnThe29thAndThe30th = day -> !day.equals(march(29)) && !day.equals(march(30));

        Optional<PlannedLateFeeCorrection> planned = plan(marchCharge("100000.00", "0.002", 18), List.of(), march(28), false, notChargedOnThe29thAndThe30th);

        assertThat(planned.get().correctedRange()).isEqualTo(new LateFeeAccrualPeriod(march(31), march(31)));
        assertThat(planned.get().correctedDays()).isEqualTo(1);
        assertThat(planned.get().creditAmount().value().amount()).isEqualByComparingTo("200.00");
        assertThat(plan(marchCharge("100000.00", "0.002", 18), List.of(), march(28), false, day -> false)).isEmpty();
    }

    @Test
    @Tag("D-375")
    void daysAlreadyCorrectedByAnyCauseAreNeverCorrectedTwice() {
        Optional<PlannedLateFeeCorrection> planned = plan(marchCharge("100000.00", "0.002", 20), List.of(applied(30, 31)), march(28), false, EVERY_DAY);

        assertThat(planned.get().correctedRange()).isEqualTo(new LateFeeAccrualPeriod(march(29), march(29)));
        assertThat(planned.get().correctedDays()).isEqualTo(1);
        assertThat(planned.get().creditAmount().value().amount()).isEqualByComparingTo("200.00");
        assertThat(plan(marchCharge("100000.00", "0.002", 20), List.of(applied(29, 31)), march(28), false, EVERY_DAY)).isEmpty();
        assertThat(plan(marchCharge("100000.00", "0.002", 20), List.of(applied(12, 31)), march(10), true, EVERY_DAY)).isEmpty();
    }

    @Test
    void theCorrectedDaysNeverExceedTheNetChargedDaysOfTheDebitNote() {
        Optional<PlannedLateFeeCorrection> planned = plan(marchCharge("100000.00", "0.002", 1), List.of(), march(28), false, EVERY_DAY);

        assertThat(planned.get().correctedRange()).isEqualTo(new LateFeeAccrualPeriod(march(29), march(29)));
        assertThat(planned.get().correctedDays()).isEqualTo(1);
        assertThat(planned.get().creditAmount().value().amount()).isEqualByComparingTo("200.00");
    }

    @Test
    @Tag("D-099")
    void theCreditIsTheHalfUpComplementOfTheNetDaysAndNotTheRoundingOfTheCorrectedDays() {
        Optional<PlannedLateFeeCorrection> planned = plan(marchCharge("100001.00", "0.0017", 20), List.of(), march(28), false, EVERY_DAY);

        assertThat(planned.get().correctedDays()).isEqualTo(3);
        assertThat(planned.get().creditAmount().value().amount()).isEqualByComparingTo("510.00");
        NonNegativeMoney directRounding = new DailySimpleLateFeeCalculator().calculateLateFee(NonNegativeMoney.of(new BigDecimal("100001.00"), Currency.ARS),
                Percentage.ofFraction(new BigDecimal("0.0017")), 3);
        assertThat(directRounding.value().amount()).isEqualByComparingTo("510.01");
    }

    @Test
    void theCauseDependsOnlyOnWhetherThePaymentWasInsideTheGrace() {
        assertThat(LateFeePaymentDateCorrectionCalculator.causeOf(true)).isEqualTo(LateFeeCorrectionCause.PAYMENT_WITHIN_GRACE);
        assertThat(LateFeePaymentDateCorrectionCalculator.causeOf(false)).isEqualTo(LateFeeCorrectionCause.PAYMENT_DATED_BEFORE_CHARGED_DAYS);
        assertThat(LateFeeCorrectionCause.PAYMENT_WITHIN_GRACE.isPaymentDate()).isTrue();
        assertThat(LateFeeCorrectionCause.PAYMENT_DATED_BEFORE_CHARGED_DAYS.isPaymentDate()).isTrue();
        assertThat(LateFeeCorrectionCause.BACKDATED_WITHDRAWAL.isPaymentDate()).isFalse();
    }

    @Test
    void theCalculatorRejectsMissingArguments() {
        LateFeeChargeSnapshot charge = marchCharge("100000.00", "0.002", 20);

        assertThatThrownBy(() -> CALCULATOR.planCorrectionOfDebitNote(null, List.of(), march(28), false, EVERY_DAY)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> CALCULATOR.planCorrectionOfDebitNote(charge, null, march(28), false, EVERY_DAY)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> CALCULATOR.planCorrectionOfDebitNote(charge, List.of(), null, false, EVERY_DAY)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> CALCULATOR.planCorrectionOfDebitNote(charge, List.of(), march(28), false, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new LateFeePaymentDateCorrectionCalculator(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void aPaymentDateCorrectionRecordKeepsTheAllocationAndTheDateAndRejectsDaysThatAreNotAfterThePayment() {
        PlannedLateFeeCorrection planned = new PlannedLateFeeCorrection(new LateFeeAccrualPeriod(march(29), march(31)), 3,
                NonNegativeMoney.of(new BigDecimal("600.00"), Currency.ARS));

        LateFeeChargeCorrection correction = LateFeeChargeCorrection.recordPaymentDateCorrection(5L, 6L, 7L, 8L, planned, 9L, march(28),
                LateFeeCorrectionCause.PAYMENT_DATED_BEFORE_CHARGED_DAYS);

        assertThat(correction.getSettlementAllocationId()).isEqualTo(9L);
        assertThat(correction.getPaymentDate()).isEqualTo(march(28));
        assertThat(correction.getEnrollmentEndDate()).isNull();
        assertThat(correction.getCause()).isEqualTo(LateFeeCorrectionCause.PAYMENT_DATED_BEFORE_CHARGED_DAYS);
        assertThat(correction.getFirstCorrectedDay()).isEqualTo(march(29));
        assertThat(correction.getLastCorrectedDay()).isEqualTo(march(31));
        assertThat(correction.getCorrectedDays()).isEqualTo(3);
        assertThat(correction.getCreatedAt()).isNotNull();
        assertThatThrownBy(() -> LateFeeChargeCorrection.recordPaymentDateCorrection(5L, 6L, 7L, 8L, planned, 9L, march(29), LateFeeCorrectionCause.PAYMENT_DATED_BEFORE_CHARGED_DAYS))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(LateFeeChargeCorrection.recordPaymentDateCorrection(5L, 6L, 7L, 8L, planned, 9L, march(20), LateFeeCorrectionCause.PAYMENT_WITHIN_GRACE).getCause())
                .isEqualTo(LateFeeCorrectionCause.PAYMENT_WITHIN_GRACE);
        assertThatThrownBy(() -> LateFeeChargeCorrection.recordPaymentDateCorrection(5L, 6L, 7L, 8L, planned, 9L, march(20), LateFeeCorrectionCause.BACKDATED_WITHDRAWAL))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LateFeeChargeCorrection.recordPaymentDateCorrection(5L, 6L, 7L, 8L, planned, null, march(20), LateFeeCorrectionCause.PAYMENT_WITHIN_GRACE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> LateFeeChargeCorrection.recordPaymentDateCorrection(5L, 6L, 7L, 8L, planned, 9L, null, LateFeeCorrectionCause.PAYMENT_WITHIN_GRACE))
                .isInstanceOf(NullPointerException.class);
    }
}
