package com.red_escuela.backend.evaluation.usecase.sectionconfig;

import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.evaluation.specialist.SectionEvaluationConfigRejectionTransaction;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class RejectSectionEvaluationConfigService implements RejectSectionEvaluationConfigUseCase {

    private final SectionEvaluationConfigRejectionTransaction transaction;

    public RejectSectionEvaluationConfigService(SectionEvaluationConfigRejectionTransaction transaction) {
        this.transaction = transaction;
    }

    @Override
    public SectionEvaluationConfig reject(RejectSectionEvaluationConfigCommand command) {
        return transaction.reject(command.sectionId(), command.request().reason());
    }
}
