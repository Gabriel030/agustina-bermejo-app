package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.evaluation.domain.ApprovalRequirement;
import com.red_escuela.backend.evaluation.domain.ApprovalRequirementCheck;
import com.red_escuela.backend.evaluation.domain.ApprovalRequirementContext;
import org.springframework.stereotype.Component;

@Component
public class PrerequisitesApprovalRequirementCheck implements ApprovalRequirementCheck {

    @Override
    public ApprovalRequirement requirement() {
        return ApprovalRequirement.PREREQUISITES;
    }

    @Override
    public boolean isUnmet(ApprovalRequirementContext context) {
        return !context.prerequisitesMet().getAsBoolean();
    }
}
