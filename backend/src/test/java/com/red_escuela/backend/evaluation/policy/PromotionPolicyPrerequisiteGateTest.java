package com.red_escuela.backend.evaluation.policy;

import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.FinalGradeType;
import com.red_escuela.backend.evaluation.domain.ClosureAlert;
import com.red_escuela.backend.evaluation.domain.CourseworkOutcome;
import com.red_escuela.backend.evaluation.domain.PrerequisiteGatedOutcome;
import com.red_escuela.backend.evaluation.entity.ClosureFinalCondition;
import com.red_escuela.backend.shared.domain.Grade;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class PromotionPolicyPrerequisiteGateTest {

    private final PromotionPolicy policy = new PromotionPolicy();

    private static CourseworkOutcome promotion() {
        return new CourseworkOutcome(EnrollmentStatus.APPROVED, Grade.of(new BigDecimal("8.0")), FinalGradeType.PROMOTION);
    }

    @Test
    void promotionWithUnmetPrerequisitesBecomesRegularGoingToFinalWithAlert() {
        PrerequisiteGatedOutcome result = policy.restrictPromotionWhenPrerequisitesUnmet(Optional.of(promotion()), () -> false);

        assertThat(result.outcome()).contains(new CourseworkOutcome(EnrollmentStatus.REGULAR, null, null));
        assertThat(result.forcedFinalCondition()).contains(ClosureFinalCondition.GOES_TO_FINAL);
        assertThat(result.alerts()).containsExactly(ClosureAlert.UNMET_PREREQUISITE);
    }

    @Test
    void promotionWithMetPrerequisitesIsLeftUntouched() {
        PrerequisiteGatedOutcome result = policy.restrictPromotionWhenPrerequisitesUnmet(Optional.of(promotion()), () -> true);

        assertThat(result.outcome()).contains(promotion());
        assertThat(result.forcedFinalCondition()).isEmpty();
        assertThat(result.alerts()).isEqualTo(List.of());
    }

    @Test
    void outcomesThatAreNotPromotionNeverConsultPrerequisites() {
        AtomicInteger consultations = new AtomicInteger();
        Optional<CourseworkOutcome> regular = Optional.of(new CourseworkOutcome(EnrollmentStatus.REGULAR, null, null));
        Optional<CourseworkOutcome> failed = Optional.of(new CourseworkOutcome(EnrollmentStatus.FAILED, null, null));
        Optional<CourseworkOutcome> approvedByFinal = Optional.of(
                new CourseworkOutcome(EnrollmentStatus.APPROVED, Grade.of(new BigDecimal("7.0")), FinalGradeType.FINAL_EXAM));

        for (Optional<CourseworkOutcome> outcome : List.of(regular, failed, approvedByFinal, Optional.<CourseworkOutcome>empty())) {
            PrerequisiteGatedOutcome result = policy.restrictPromotionWhenPrerequisitesUnmet(outcome, () -> {
                consultations.incrementAndGet();
                return false;
            });
            assertThat(result.outcome()).isEqualTo(outcome);
            assertThat(result.alerts()).isEmpty();
        }
        assertThat(consultations).hasValue(0);
    }

    private static CourseworkOutcome approvedByFinal() {
        return new CourseworkOutcome(EnrollmentStatus.APPROVED, Grade.of(new BigDecimal("7.0")), FinalGradeType.FINAL_EXAM);
    }

    @Test
    void approvalByFinalWithUnmetPrerequisitesBecomesRegularWithAlert() {
        PrerequisiteGatedOutcome result = policy.restrictApprovalWhenPrerequisitesUnmet(Optional.of(approvedByFinal()), () -> false);

        assertThat(result.outcome()).contains(new CourseworkOutcome(EnrollmentStatus.REGULAR, null, null));
        assertThat(result.alerts()).containsExactly(ClosureAlert.UNMET_PREREQUISITE);
    }

    @Test
    void approvalByFinalWithMetPrerequisitesIsLeftUntouched() {
        PrerequisiteGatedOutcome result = policy.restrictApprovalWhenPrerequisitesUnmet(Optional.of(approvedByFinal()), () -> true);

        assertThat(result.outcome()).contains(approvedByFinal());
        assertThat(result.alerts()).isEmpty();
    }

    @Test
    void approvalGateOnlyConsultsPrerequisitesForApprovedOutcomes() {
        AtomicInteger consultations = new AtomicInteger();
        for (Optional<CourseworkOutcome> outcome : List.of(
                Optional.of(new CourseworkOutcome(EnrollmentStatus.REGULAR, null, null)),
                Optional.of(new CourseworkOutcome(EnrollmentStatus.FAILED, null, null)),
                Optional.<CourseworkOutcome>empty())) {
            PrerequisiteGatedOutcome result = policy.restrictApprovalWhenPrerequisitesUnmet(outcome, () -> {
                consultations.incrementAndGet();
                return false;
            });
            assertThat(result.outcome()).isEqualTo(outcome);
        }
        assertThat(consultations).hasValue(0);
    }
}
