package com.red_escuela.backend.evaluation.usecase.sectionevaluationconfig;

import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.evaluation.service.SectionEvaluationConfigFinder;
import com.red_escuela.backend.evaluation.service.SectionEvaluationConfigInstructorAccess;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetMySectionEvaluationConfigService implements GetMySectionEvaluationConfigUseCase {

    private final SectionEvaluationConfigFinder configFinder;
    private final SectionEvaluationConfigInstructorAccess instructorAccess;

    public GetMySectionEvaluationConfigService(SectionEvaluationConfigFinder configFinder,
                                                SectionEvaluationConfigInstructorAccess instructorAccess) {
        this.configFinder = configFinder;
        this.instructorAccess = instructorAccess;
    }

    @Override
    public SectionEvaluationConfig get(GetMySectionEvaluationConfigCommand command) {
        instructorAccess.requireAssignedToSection(command.sectionId(), command.instructorId());
        return configFinder.findOrThrow(command.sectionId());
    }
}
