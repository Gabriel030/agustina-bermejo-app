package com.red_escuela.backend.evaluation.usecase.sectionconfig;

import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.evaluation.specialist.SectionEvaluationConfigApprovalTransaction;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ApproveSectionEvaluationConfigService implements ApproveSectionEvaluationConfigUseCase {

    private final SectionEvaluationConfigApprovalTransaction transaction;

    public ApproveSectionEvaluationConfigService(SectionEvaluationConfigApprovalTransaction transaction) {
        this.transaction = transaction;
    }

    @Override
    public SectionEvaluationConfig approve(ApproveSectionEvaluationConfigCommand command) {
        return transaction.approve(command.sectionId());
    }
}
