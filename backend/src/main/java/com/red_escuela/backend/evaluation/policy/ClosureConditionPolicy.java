package com.red_escuela.backend.evaluation.policy;

import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.evaluation.api.PaymentsStanding;
import com.red_escuela.backend.evaluation.domain.CourseworkOutcome;
import com.red_escuela.backend.evaluation.entity.ClosureFinalCondition;

import java.util.Optional;

public final class ClosureConditionPolicy {

    public Optional<ClosureFinalCondition> decideFinalCondition(
            Optional<CourseworkOutcome> outcome, boolean forcedToFinal, EnrollmentStatus currentStatus,
            boolean freeByAbsences, PaymentsStanding payments, boolean documentsComplete) {
        if (outcome.filter(result -> result.status() == EnrollmentStatus.FAILED).isPresent()) {
            return Optional.of(ClosureFinalCondition.FAILED);
        }
        if (freeByAbsences && currentStatus != EnrollmentStatus.REGULAR) {
            return Optional.of(ClosureFinalCondition.FREE);
        }
        if (forcedToFinal) {
            return Optional.of(ClosureFinalCondition.GOES_TO_FINAL);
        }
        if (outcome.filter(CourseworkOutcome::isPromotion).isPresent()) {
            boolean administrativeBlocked = payments == PaymentsStanding.NOT_OK || !documentsComplete;
            return Optional.of(administrativeBlocked ? ClosureFinalCondition.PROMOTION_CONDITIONAL : ClosureFinalCondition.PROMOTES);
        }
        if (currentStatus == EnrollmentStatus.REGULAR
                || outcome.filter(result -> result.status() == EnrollmentStatus.REGULAR).isPresent()) {
            return Optional.of(ClosureFinalCondition.GOES_TO_FINAL);
        }
        return Optional.empty();
    }
}
