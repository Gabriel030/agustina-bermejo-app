package com.red_escuela.backend.evaluation.usecase.sectionconfig;

import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.evaluation.specialist.SectionEvaluationConfigSubmissionTransaction;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class SubmitSectionEvaluationConfigForApprovalService implements SubmitSectionEvaluationConfigForApprovalUseCase {

    private final SectionEvaluationConfigSubmissionTransaction transaction;

    public SubmitSectionEvaluationConfigForApprovalService(SectionEvaluationConfigSubmissionTransaction transaction) {
        this.transaction = transaction;
    }

    @Override
    public SectionEvaluationConfig submit(SubmitSectionEvaluationConfigForApprovalCommand command) {
        return transaction.submit(command.sectionId(), command.instructorId());
    }
}
