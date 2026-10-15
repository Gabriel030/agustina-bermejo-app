package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

public final class LateFeeWithdrawalCorrectionCalculator {

    private final LateFeeCalculationStrategy calculationStrategy;

    public LateFeeWithdrawalCorrectionCalculator(LateFeeCalculationStrategy calculationStrategy) {
        this.calculationStrategy = Objects.requireNonNull(calculationStrategy, "calculationStrategy");
    }

    public Optional<PlannedLateFeeCorrection> planCorrectionOfDebitNote(LateFeeChargeSnapshot charge, List<AppliedLateFeeCorrection> alreadyCorrected,
                                                                       LocalDate enrollmentEndDate, boolean frozenByRuleOfTheEndDate,
                                                                       Predicate<LocalDate> chargingEnabledOn) {
        Objects.requireNonNull(charge, "charge");
        Objects.requireNonNull(alreadyCorrected, "alreadyCorrected");
        Objects.requireNonNull(enrollmentEndDate, "enrollmentEndDate");
        Objects.requireNonNull(chargingEnabledOn, "chargingEnabledOn");
        if (!frozenByRuleOfTheEndDate) {
            return Optional.empty();
        }
        LateFeeAccrualPeriod period = charge.period();
        int alreadyCorrectedDays = alreadyCorrected.stream().mapToInt(AppliedLateFeeCorrection::correctedDays).sum();
        int netChargedDays = charge.chargedDays() - alreadyCorrectedDays;
        LocalDate first = null;
        LocalDate last = null;
        int candidates = 0;
        LocalDate firstCandidate = period.firstDay().isAfter(enrollmentEndDate) ? period.firstDay() : enrollmentEndDate.plusDays(1);
        for (LocalDate day = firstCandidate; !day.isAfter(period.lastDay()); day = day.plusDays(1)) {
            if (chargingEnabledOn.test(day) && !isCovered(day, alreadyCorrected)) {
                first = first == null ? day : first;
                last = day;
                candidates++;
            }
        }
        int correctedDays = Math.min(candidates, Math.max(netChargedDays, 0));
        if (correctedDays == 0) {
            return Optional.empty();
        }
        if (correctedDays < candidates) {
            last = lastDayOfFirstCandidates(first, period, chargingEnabledOn, alreadyCorrected, correctedDays);
        }
        NonNegativeMoney credit = complementOf(charge, netChargedDays, correctedDays);
        return Optional.of(new PlannedLateFeeCorrection(new LateFeeAccrualPeriod(first, last), correctedDays, credit));
    }

    private NonNegativeMoney complementOf(LateFeeChargeSnapshot charge, int netChargedDays, int correctedDays) {
        NonNegativeMoney before = calculationStrategy.calculateLateFee(charge.baseAmount(), charge.dailyRate(), netChargedDays);
        NonNegativeMoney after = calculationStrategy.calculateLateFee(charge.baseAmount(), charge.dailyRate(), netChargedDays - correctedDays);
        return NonNegativeMoney.of(before.minus(after));
    }

    private static boolean isCovered(LocalDate day, List<AppliedLateFeeCorrection> alreadyCorrected) {
        return alreadyCorrected.stream().anyMatch(applied -> !day.isBefore(applied.correctedRange().firstDay()) && !day.isAfter(applied.correctedRange().lastDay()));
    }

    private static LocalDate lastDayOfFirstCandidates(LocalDate first, LateFeeAccrualPeriod period, Predicate<LocalDate> chargingEnabledOn,
                                                      List<AppliedLateFeeCorrection> alreadyCorrected, int count) {
        LocalDate last = first;
        int taken = 0;
        for (LocalDate day = first; !day.isAfter(period.lastDay()) && taken < count; day = day.plusDays(1)) {
            if (chargingEnabledOn.test(day) && !isCovered(day, alreadyCorrected)) {
                last = day;
                taken++;
            }
        }
        return last;
    }
}
