package com.red_escuela.backend.evaluation.usecase.sectionconfig;

import com.red_escuela.backend.evaluation.dto.SectionEvaluationConfigResponse;
import com.red_escuela.backend.evaluation.specialist.SectionEvaluationConfigApprovalTransaction;
import com.red_escuela.backend.evaluation.specialist.SectionEvaluationConfigResponseAssembler;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ApproveSectionEvaluationConfigService implements ApproveSectionEvaluationConfigUseCase {

    private final SectionEvaluationConfigApprovalTransaction transaction;
    private final SectionEvaluationConfigResponseAssembler responseAssembler;

    public ApproveSectionEvaluationConfigService(SectionEvaluationConfigApprovalTransaction transaction,
                                                 SectionEvaluationConfigResponseAssembler responseAssembler) {
        this.transaction = transaction;
        this.responseAssembler = responseAssembler;
    }

    @Override
    public SectionEvaluationConfigResponse approve(ApproveSectionEvaluationConfigCommand command) {
        return responseAssembler.assembleForStaff(transaction.approve(command.sectionId()));
    }
}
