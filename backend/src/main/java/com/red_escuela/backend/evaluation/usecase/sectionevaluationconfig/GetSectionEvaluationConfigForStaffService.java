package com.red_escuela.backend.evaluation.usecase.sectionevaluationconfig;

import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.evaluation.service.SectionEvaluationConfigFinder;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetSectionEvaluationConfigForStaffService implements GetSectionEvaluationConfigForStaffUseCase {

    private final SectionEvaluationConfigFinder configFinder;

    public GetSectionEvaluationConfigForStaffService(SectionEvaluationConfigFinder configFinder) {
        this.configFinder = configFinder;
    }

    @Override
    public SectionEvaluationConfig get(Long sectionId) {
        return configFinder.findOrThrow(sectionId);
    }
}
