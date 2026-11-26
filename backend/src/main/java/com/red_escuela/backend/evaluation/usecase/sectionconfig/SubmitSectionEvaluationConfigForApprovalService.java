package com.red_escuela.backend.evaluation.usecase.sectionconfig;

import com.red_escuela.backend.evaluation.dto.SectionEvaluationConfigResponse;
import com.red_escuela.backend.evaluation.specialist.SectionEvaluationConfigResponseAssembler;
import com.red_escuela.backend.evaluation.specialist.SectionEvaluationConfigSubmissionTransaction;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class SubmitSectionEvaluationConfigForApprovalService implements SubmitSectionEvaluationConfigForApprovalUseCase {

    private final SectionEvaluationConfigSubmissionTransaction transaction;
    private final SectionEvaluationConfigResponseAssembler responseAssembler;

    public SubmitSectionEvaluationConfigForApprovalService(SectionEvaluationConfigSubmissionTransaction transaction,
                                                           SectionEvaluationConfigResponseAssembler responseAssembler) {
        this.transaction = transaction;
        this.responseAssembler = responseAssembler;
    }

    @Override
    public SectionEvaluationConfigResponse submit(SubmitSectionEvaluationConfigForApprovalCommand command) {
        return responseAssembler.assembleForInstructor(
                transaction.submit(command.sectionId(), command.instructorId()), command.instructorId());
    }
}
