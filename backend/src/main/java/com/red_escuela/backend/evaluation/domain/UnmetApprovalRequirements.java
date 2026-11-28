package com.red_escuela.backend.evaluation.domain;

import java.util.List;
import java.util.Optional;

public record UnmetApprovalRequirements(List<ApprovalRequirement> requirements, Optional<String> detail) {

    public static UnmetApprovalRequirements none() {
        return new UnmetApprovalRequirements(List.of(), Optional.empty());
    }

    public static UnmetApprovalRequirements of(List<ApprovalRequirement> requirements) {
        return new UnmetApprovalRequirements(List.copyOf(requirements), Optional.empty());
    }

    public boolean isEmpty() {
        return requirements.isEmpty();
    }

    public boolean contains(ApprovalRequirement requirement) {
        return requirements.contains(requirement);
    }
}
