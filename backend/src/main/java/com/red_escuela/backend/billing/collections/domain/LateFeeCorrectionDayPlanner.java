package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

final class LateFeeCorrectionDayPlanner {

    private final LateFeeCalculationStrategy calculationStrategy;

    LateFeeCorrectionDayPlanner(LateFeeCalculationStrategy calculationStrategy) {
        this.calculationStrategy = Objects.requireNonNull(calculationStrategy, "calculationStrategy");
    }

    Optional<PlannedLateFeeCorrection> planCorrectionOfCandidateDays(LateFeeChargeSnapshot charge, List<AppliedLateFeeCorrection> alreadyCorrected,
                                                                      Predicate<LocalDate> isCandidateDay) {
        LateFeeAccrualPeriod period = charge.period();
        int alreadyCorrectedDays = alreadyCorrected.stream().mapToInt(AppliedLateFeeCorrection::correctedDays).sum();
        int netChargedDays = charge.chargedDays() - alreadyCorrectedDays;
        List<LocalDate> candidates = period.firstDay().datesUntil(period.lastDay().plusDays(1))
                .filter(day -> isCandidateDay.test(day) && !isCovered(day, alreadyCorrected)).toList();
        int correctedDays = Math.min(candidates.size(), Math.max(netChargedDays, 0));
        if (correctedDays == 0) {
            return Optional.empty();
        }
        LocalDate first = candidates.get(0);
        LocalDate last = candidates.get(correctedDays - 1);
        return Optional.of(new PlannedLateFeeCorrection(new LateFeeAccrualPeriod(first, last), correctedDays, complementOf(charge, netChargedDays, correctedDays)));
    }

    private NonNegativeMoney complementOf(LateFeeChargeSnapshot charge, int netChargedDays, int correctedDays) {
        NonNegativeMoney before = calculationStrategy.calculateLateFee(charge.baseAmount(), charge.dailyRate(), netChargedDays);
        NonNegativeMoney after = calculationStrategy.calculateLateFee(charge.baseAmount(), charge.dailyRate(), netChargedDays - correctedDays);
        return NonNegativeMoney.of(before.minus(after));
    }

    private static boolean isCovered(LocalDate day, List<AppliedLateFeeCorrection> alreadyCorrected) {
        return alreadyCorrected.stream().anyMatch(applied -> !day.isBefore(applied.correctedRange().firstDay()) && !day.isAfter(applied.correctedRange().lastDay()));
    }
}
