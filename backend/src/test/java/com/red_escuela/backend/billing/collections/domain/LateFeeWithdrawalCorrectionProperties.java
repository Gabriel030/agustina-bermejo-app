package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.Percentage;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tag;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-375")
@Tag("D-099")
class LateFeeWithdrawalCorrectionProperties {
    private static final DailySimpleLateFeeCalculator STRATEGY = new DailySimpleLateFeeCalculator();
    private static final LateFeeWithdrawalCorrectionCalculator CALCULATOR = new LateFeeWithdrawalCorrectionCalculator(STRATEGY);
    private static final LocalDate PERIOD_FIRST = LocalDate.of(2027, 4, 12);
    private static final LocalDate PERIOD_LAST = LocalDate.of(2027, 4, 30);

    record Case(long baseCents, int rateTenThousandths, int endOffset, int disabledMask, int firstEndOffset) {
    }

    @Provide
    Arbitrary<Case> cases() {
        return Combinators.combine(Arbitraries.longs().between(1L, 99_999_999L), Arbitraries.integers().between(1, 90), Arbitraries.integers().between(-3, 22),
                Arbitraries.integers().between(0, (1 << 19) - 1), Arbitraries.integers().between(-3, 22)).as(Case::new);
    }

    private static Predicate<LocalDate> enabledOn(int disabledMask) {
        return day -> day.isBefore(PERIOD_FIRST) || day.isAfter(PERIOD_LAST) || (disabledMask & (1 << (day.getDayOfMonth() - 12))) == 0;
    }

    private static LateFeeChargeSnapshot chargeOf(Case c) {
        Predicate<LocalDate> enabled = enabledOn(c.disabledMask());
        int charged = 0;
        for (LocalDate day = PERIOD_FIRST; !day.isAfter(PERIOD_LAST); day = day.plusDays(1)) {
            charged += enabled.test(day) ? 1 : 0;
        }
        return charged == 0 ? null : new LateFeeChargeSnapshot(1L, 2L, new LateFeeAccrualPeriod(PERIOD_FIRST, PERIOD_LAST), charged,
                NonNegativeMoney.of(BigDecimal.valueOf(c.baseCents(), 2), Currency.ARS), Percentage.ofFraction(BigDecimal.valueOf(c.rateTenThousandths(), 5)));
    }

    @Property(tries = 400)
    void creditsPlusWhatRemainsAlwaysAddUpToTheTotalAndTheRemainingIsWhatTheEnabledDaysUpToTheEndWouldHaveCost(@ForAll("cases") Case c) {
        LateFeeChargeSnapshot charge = chargeOf(c);
        if (charge == null) {
            return;
        }
        Predicate<LocalDate> enabled = enabledOn(c.disabledMask());
        LocalDate end = LocalDate.of(2027, 4, 30).plusDays(c.endOffset() - 20);
        List<AppliedLateFeeCorrection> applied = new ArrayList<>();
        NonNegativeMoney total = STRATEGY.calculateLateFee(charge.baseAmount(), charge.dailyRate(), charge.chargedDays());
        BigDecimal credited = BigDecimal.ZERO;
        int correctedDays = 0;
        for (int round = 0; round < 3; round++) {
            Optional<PlannedLateFeeCorrection> planned = CALCULATOR.planCorrectionOfDebitNote(charge, applied, end, true, enabled);
            if (round > 0) {
                assertThat(planned).isEmpty();
            }
            if (planned.isPresent()) {
                assertThat(planned.get().creditAmount().value().amount()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
                assertThat(planned.get().correctedRange().firstDay()).isAfter(end);
                credited = credited.add(planned.get().creditAmount().value().amount());
                correctedDays += planned.get().correctedDays();
                applied.add(new AppliedLateFeeCorrection(planned.get().correctedRange(), planned.get().correctedDays()));
            }
        }
        int daysUpToTheEnd = 0;
        for (LocalDate day = PERIOD_FIRST; !day.isAfter(PERIOD_LAST); day = day.plusDays(1)) {
            daysUpToTheEnd += enabled.test(day) && !day.isAfter(end) ? 1 : 0;
        }
        BigDecimal remaining = total.value().amount().subtract(credited);
        assertThat(correctedDays).isEqualTo(charge.chargedDays() - daysUpToTheEnd);
        assertThat(remaining).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(remaining).isEqualByComparingTo(STRATEGY.calculateLateFee(charge.baseAmount(), charge.dailyRate(), daysUpToTheEnd).value().amount());
    }

    @Property(tries = 200)
    void aCorrectionInTwoStepsLeavesTheSameRemainingAsOneStep(@ForAll("cases") Case c) {
        LateFeeChargeSnapshot charge = chargeOf(c);
        if (charge == null) {
            return;
        }
        Predicate<LocalDate> enabled = enabledOn(c.disabledMask());
        LocalDate laterEnd = LocalDate.of(2027, 4, 30).plusDays(c.endOffset() - 20);
        LocalDate earlierEnd = LocalDate.of(2027, 4, 30).plusDays(c.firstEndOffset() - 20);
        if (!earlierEnd.isBefore(laterEnd)) {
            return;
        }
        Optional<PlannedLateFeeCorrection> first = CALCULATOR.planCorrectionOfDebitNote(charge, List.of(), laterEnd, true, enabled);
        List<AppliedLateFeeCorrection> applied = first.map(p -> List.of(new AppliedLateFeeCorrection(p.correctedRange(), p.correctedDays()))).orElse(List.of());
        Optional<PlannedLateFeeCorrection> second = CALCULATOR.planCorrectionOfDebitNote(charge, applied, earlierEnd, true, enabled);
        Optional<PlannedLateFeeCorrection> direct = CALCULATOR.planCorrectionOfDebitNote(charge, List.of(), earlierEnd, true, enabled);
        BigDecimal stepwise = first.map(p -> p.creditAmount().value().amount()).orElse(BigDecimal.ZERO).add(second.map(p -> p.creditAmount().value().amount()).orElse(BigDecimal.ZERO));
        assertThat(stepwise).isEqualByComparingTo(direct.map(p -> p.creditAmount().value().amount()).orElse(BigDecimal.ZERO));
    }
}
