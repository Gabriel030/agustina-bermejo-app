package com.red_escuela.backend.evaluation.domain;

public interface ApprovalRequirementCheck {

    ApprovalRequirement requirement();

    boolean isUnmet(ApprovalRequirementContext context);
}
