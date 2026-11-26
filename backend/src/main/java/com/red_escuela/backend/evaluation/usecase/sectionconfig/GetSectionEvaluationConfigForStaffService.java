package com.red_escuela.backend.evaluation.usecase.sectionconfig;

import com.red_escuela.backend.evaluation.dto.SectionEvaluationConfigResponse;
import com.red_escuela.backend.evaluation.specialist.SectionEvaluationConfigFinder;
import com.red_escuela.backend.evaluation.specialist.SectionEvaluationConfigResponseAssembler;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetSectionEvaluationConfigForStaffService implements GetSectionEvaluationConfigForStaffUseCase {

    private final SectionEvaluationConfigFinder configFinder;
    private final SectionEvaluationConfigResponseAssembler responseAssembler;

    public GetSectionEvaluationConfigForStaffService(SectionEvaluationConfigFinder configFinder,
                                                     SectionEvaluationConfigResponseAssembler responseAssembler) {
        this.configFinder = configFinder;
        this.responseAssembler = responseAssembler;
    }

    @Override
    public SectionEvaluationConfigResponse get(Long sectionId) {
        return responseAssembler.assembleForStaff(configFinder.findOrThrow(sectionId));
    }
}
