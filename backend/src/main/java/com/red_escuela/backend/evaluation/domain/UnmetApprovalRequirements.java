package com.red_escuela.backend.evaluation.domain;

import java.util.List;

public record UnmetApprovalRequirements(List<ApprovalRequirement> requirements) {

    public static UnmetApprovalRequirements none() {
        return new UnmetApprovalRequirements(List.of());
    }

    public static UnmetApprovalRequirements of(List<ApprovalRequirement> requirements) {
        return new UnmetApprovalRequirements(List.copyOf(requirements));
    }

    public boolean isEmpty() {
        return requirements.isEmpty();
    }

    public boolean contains(ApprovalRequirement requirement) {
        return requirements.contains(requirement);
    }
}
