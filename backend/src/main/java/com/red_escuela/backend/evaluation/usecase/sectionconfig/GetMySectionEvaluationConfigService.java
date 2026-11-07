package com.red_escuela.backend.evaluation.usecase.sectionconfig;

import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.evaluation.specialist.SectionEvaluationConfigFinder;
import com.red_escuela.backend.evaluation.specialist.SectionEvaluationConfigInstructorAccessChecker;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetMySectionEvaluationConfigService implements GetMySectionEvaluationConfigUseCase {

    private final SectionEvaluationConfigFinder configFinder;
    private final SectionEvaluationConfigInstructorAccessChecker instructorAccess;

    public GetMySectionEvaluationConfigService(SectionEvaluationConfigFinder configFinder,
                                                SectionEvaluationConfigInstructorAccessChecker instructorAccess) {
        this.configFinder = configFinder;
        this.instructorAccess = instructorAccess;
    }

    @Override
    public SectionEvaluationConfig get(GetMySectionEvaluationConfigCommand command) {
        instructorAccess.requireAssignedToSection(command.sectionId(), command.instructorId());
        return configFinder.findOrThrow(command.sectionId());
    }
}
