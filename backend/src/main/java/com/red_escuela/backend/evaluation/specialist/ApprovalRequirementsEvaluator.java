package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.evaluation.domain.ApprovalRequirement;
import com.red_escuela.backend.evaluation.domain.ApprovalRequirementCheck;
import com.red_escuela.backend.evaluation.domain.ApprovalRequirementContext;
import com.red_escuela.backend.evaluation.domain.UnmetApprovalRequirements;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ApprovalRequirementsEvaluator {

    private final List<ApprovalRequirementCheck> checks;

    public ApprovalRequirementsEvaluator(List<ApprovalRequirementCheck> checks) {
        this.checks = List.copyOf(checks);
    }

    public UnmetApprovalRequirements evaluate(ApprovalRequirementContext context) {
        List<ApprovalRequirement> unmet = checks.stream()
                .filter(check -> check.isUnmet(context))
                .map(ApprovalRequirementCheck::requirement)
                .sorted()
                .toList();
        return UnmetApprovalRequirements.of(unmet);
    }
}
