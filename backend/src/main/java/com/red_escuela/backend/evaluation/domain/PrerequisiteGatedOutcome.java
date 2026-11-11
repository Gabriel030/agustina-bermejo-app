package com.red_escuela.backend.evaluation.domain;

import com.red_escuela.backend.evaluation.entity.ClosureFinalCondition;

import java.util.List;
import java.util.Optional;

public record PrerequisiteGatedOutcome(
        Optional<CourseworkOutcome> outcome, Optional<ClosureFinalCondition> forcedFinalCondition, List<ClosureAlert> alerts) {

    public static PrerequisiteGatedOutcome unchanged(Optional<CourseworkOutcome> outcome) {
        return new PrerequisiteGatedOutcome(outcome, Optional.empty(), List.of());
    }

    public static PrerequisiteGatedOutcome restrictedToFinal(CourseworkOutcome outcome) {
        return new PrerequisiteGatedOutcome(
                Optional.of(outcome), Optional.of(ClosureFinalCondition.GOES_TO_FINAL), List.of(ClosureAlert.UNMET_PREREQUISITE));
    }
}
