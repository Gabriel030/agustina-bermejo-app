package com.red_escuela.backend.evaluation.usecase.sectionconfig;

import com.red_escuela.backend.evaluation.dto.SectionEvaluationConfigResponse;
import com.red_escuela.backend.evaluation.specialist.SectionEvaluationConfigRejectionTransaction;
import com.red_escuela.backend.evaluation.specialist.SectionEvaluationConfigResponseAssembler;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class RejectSectionEvaluationConfigService implements RejectSectionEvaluationConfigUseCase {

    private final SectionEvaluationConfigRejectionTransaction transaction;
    private final SectionEvaluationConfigResponseAssembler responseAssembler;

    public RejectSectionEvaluationConfigService(SectionEvaluationConfigRejectionTransaction transaction,
                                                SectionEvaluationConfigResponseAssembler responseAssembler) {
        this.transaction = transaction;
        this.responseAssembler = responseAssembler;
    }

    @Override
    public SectionEvaluationConfigResponse reject(RejectSectionEvaluationConfigCommand command) {
        return responseAssembler.assembleForStaff(transaction.reject(command.sectionId(), command.request().reason()));
    }
}
