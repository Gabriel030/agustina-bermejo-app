package com.red_escuela.backend.billing.reporting.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Optional;

public final class ReceiptReviewTally {

    private final int reviewTargetBusinessDays;
    private long uploaded;
    private long approved;
    private long appliedToDebt;
    private long rejected;
    private long reviewed;
    private long reviewedAfterTarget;
    private long reviewBusinessDaysTotal;

    public ReceiptReviewTally(int reviewTargetBusinessDays) {
        if (reviewTargetBusinessDays < 1) {
            throw new IllegalArgumentException("The review target must be at least one business day");
        }
        this.reviewTargetBusinessDays = reviewTargetBusinessDays;
    }

    public void recordUpload(ReceiptOutcome outcome, LocalDate uploadedOn, LocalDate reviewedOn) {
        uploaded++;
        switch (outcome) {
            case APPROVED -> approved++;
            case APPLIED_TO_DEBT -> appliedToDebt++;
            case REJECTED -> rejected++;
            case PENDING_REVIEW -> {
            }
        }
        if (reviewedOn != null) {
            int businessDays = BusinessDays.elapsedBetween(uploadedOn, reviewedOn);
            reviewed++;
            reviewBusinessDaysTotal += businessDays;
            if (businessDays > reviewTargetBusinessDays) {
                reviewedAfterTarget++;
            }
        }
    }

    public long uploaded() {
        return uploaded;
    }

    public long approved() {
        return approved;
    }

    public long appliedToDebt() {
        return appliedToDebt;
    }

    public long rejected() {
        return rejected;
    }

    public long reviewedAfterTarget() {
        return reviewedAfterTarget;
    }

    public Optional<DaysValue> averageReviewBusinessDays() {
        if (reviewed == 0) {
            return Optional.empty();
        }
        return Optional.of(new DaysValue(BigDecimal.valueOf(reviewBusinessDaysTotal).divide(BigDecimal.valueOf(reviewed), 2, RoundingMode.HALF_UP)));
    }
}
