package com.red_escuela.backend.billing.collections.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

public final class LateFeePaymentDateCorrectionCalculator {

    private final LateFeeCorrectionDayPlanner dayPlanner;

    public LateFeePaymentDateCorrectionCalculator(LateFeeCalculationStrategy calculationStrategy) {
        this.dayPlanner = new LateFeeCorrectionDayPlanner(calculationStrategy);
    }

    public Optional<PlannedLateFeeCorrection> planCorrectionOfDebitNote(LateFeeChargeSnapshot charge, List<AppliedLateFeeCorrection> alreadyCorrected,
                                                                       LocalDate paymentDate, boolean paidWithinGrace, Predicate<LocalDate> chargedOn) {
        Objects.requireNonNull(charge, "charge");
        Objects.requireNonNull(alreadyCorrected, "alreadyCorrected");
        Objects.requireNonNull(paymentDate, "paymentDate");
        Objects.requireNonNull(chargedOn, "chargedOn");
        return dayPlanner.planCorrectionOfCandidateDays(charge, alreadyCorrected, day -> (paidWithinGrace || day.isAfter(paymentDate)) && chargedOn.test(day));
    }

    public static LateFeeCorrectionCause causeOf(boolean paidWithinGrace) {
        return paidWithinGrace ? LateFeeCorrectionCause.PAYMENT_WITHIN_GRACE : LateFeeCorrectionCause.PAYMENT_DATED_BEFORE_CHARGED_DAYS;
    }
}
