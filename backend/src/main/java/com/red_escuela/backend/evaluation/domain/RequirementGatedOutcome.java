package com.red_escuela.backend.evaluation.domain;

import com.red_escuela.backend.evaluation.entity.ClosureFinalCondition;

import java.util.List;
import java.util.Optional;

public record RequirementGatedOutcome(
        Optional<CourseworkOutcome> outcome,
        Optional<ClosureFinalCondition> forcedFinalCondition,
        List<ClosureAlert> alerts,
        UnmetApprovalRequirements pendingRequirements) {

    public static RequirementGatedOutcome unchanged(Optional<CourseworkOutcome> outcome) {
        return new RequirementGatedOutcome(outcome, Optional.empty(), List.of(), UnmetApprovalRequirements.none());
    }

    public static RequirementGatedOutcome restrictedToFinal(CourseworkOutcome outcome, UnmetApprovalRequirements pendingRequirements) {
        List<ClosureAlert> alerts = pendingRequirements.contains(ApprovalRequirement.PREREQUISITES)
                ? List.of(ClosureAlert.UNMET_PREREQUISITE) : List.of();
        return new RequirementGatedOutcome(
                Optional.of(outcome), Optional.of(ClosureFinalCondition.GOES_TO_FINAL), alerts, pendingRequirements);
    }
}
