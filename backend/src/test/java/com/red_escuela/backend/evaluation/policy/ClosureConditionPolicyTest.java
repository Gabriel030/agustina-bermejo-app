package com.red_escuela.backend.evaluation.policy;

import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.FinalGradeType;
import com.red_escuela.backend.evaluation.api.PaymentsStanding;
import com.red_escuela.backend.evaluation.domain.CourseworkOutcome;
import com.red_escuela.backend.evaluation.entity.ClosureFinalCondition;
import com.red_escuela.backend.shared.domain.Grade;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ClosureConditionPolicyTest {

    private final ClosureConditionPolicy policy = new ClosureConditionPolicy();
    private final Optional<CourseworkOutcome> promotion = Optional.of(new CourseworkOutcome(
            EnrollmentStatus.APPROVED, Grade.of(BigDecimal.valueOf(8)), FinalGradeType.PROMOTION));
    private final Optional<CourseworkOutcome> regular =
            Optional.of(new CourseworkOutcome(EnrollmentStatus.REGULAR, null, null));
    private final Optional<CourseworkOutcome> failed =
            Optional.of(new CourseworkOutcome(EnrollmentStatus.FAILED, null, null));

    private Optional<ClosureFinalCondition> decide(Optional<CourseworkOutcome> outcome, boolean forced,
            EnrollmentStatus current, boolean free, PaymentsStanding payments, boolean documentsComplete) {
        return policy.decideFinalCondition(outcome, forced, current, free, payments, documentsComplete);
    }

    @Test
    void promotionWithPaymentsOkAndCompleteDocumentsPromotes() {
        assertThat(decide(promotion, false, EnrollmentStatus.IN_PROGRESS, false, PaymentsStanding.OK, true))
                .contains(ClosureFinalCondition.PROMOTES);
    }

    @Test
    void promotionWithOverduePaymentsIsConditional() {
        assertThat(decide(promotion, false, EnrollmentStatus.IN_PROGRESS, false, PaymentsStanding.NOT_OK, true))
                .contains(ClosureFinalCondition.PROMOTION_CONDITIONAL);
    }

    @Test
    void promotionWithIncompleteDocumentsIsConditional() {
        assertThat(decide(promotion, false, EnrollmentStatus.IN_PROGRESS, false, PaymentsStanding.OK, false))
                .contains(ClosureFinalCondition.PROMOTION_CONDITIONAL);
    }

    @Test
    void notVerifiedPaymentsDoNotBlockPromotion() {
        assertThat(decide(promotion, false, EnrollmentStatus.IN_PROGRESS, false, PaymentsStanding.NOT_VERIFIED, true))
                .contains(ClosureFinalCondition.PROMOTES);
    }

    @Test
    void forcedFinalWinsOverPromotionAndAdministrativeBlocks() {
        assertThat(decide(regular, true, EnrollmentStatus.IN_PROGRESS, false, PaymentsStanding.NOT_OK, false))
                .contains(ClosureFinalCondition.GOES_TO_FINAL);
    }

    @Test
    void regularOutcomeGoesToFinal() {
        assertThat(decide(regular, false, EnrollmentStatus.IN_PROGRESS, false, PaymentsStanding.OK, true))
                .contains(ClosureFinalCondition.GOES_TO_FINAL);
    }

    @Test
    void alreadyRegularEnrollmentGoesToFinalEvenWhenFreeByAbsences() {
        assertThat(decide(Optional.empty(), false, EnrollmentStatus.REGULAR, true, PaymentsStanding.OK, true))
                .contains(ClosureFinalCondition.GOES_TO_FINAL);
    }

    @Test
    void freeByAbsencesWithoutRegularityIsFree() {
        assertThat(decide(promotion, false, EnrollmentStatus.IN_PROGRESS, true, PaymentsStanding.OK, true))
                .contains(ClosureFinalCondition.FREE);
    }

    @Test
    void failedOutcomeIsFailedEvenWhenFree() {
        assertThat(decide(failed, false, EnrollmentStatus.IN_PROGRESS, true, PaymentsStanding.OK, true))
                .contains(ClosureFinalCondition.FAILED);
    }

    @Test
    void withoutOutcomeTheConditionIsUndecided() {
        assertThat(decide(Optional.empty(), false, EnrollmentStatus.IN_PROGRESS, false, PaymentsStanding.OK, true)).isEmpty();
    }
}
