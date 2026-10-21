package com.red_escuela.backend.billing.collections.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

public final class LateFeeWithdrawalCorrectionCalculator {

    private final LateFeeCorrectionDayPlanner dayPlanner;

    public LateFeeWithdrawalCorrectionCalculator(LateFeeCalculationStrategy calculationStrategy) {
        this.dayPlanner = new LateFeeCorrectionDayPlanner(calculationStrategy);
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
        return dayPlanner.planCorrectionOfCandidateDays(charge, alreadyCorrected, day -> day.isAfter(enrollmentEndDate) && chargingEnabledOn.test(day));
    }
}
