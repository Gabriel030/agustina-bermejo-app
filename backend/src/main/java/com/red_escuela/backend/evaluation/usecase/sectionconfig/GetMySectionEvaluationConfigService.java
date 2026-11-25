package com.red_escuela.backend.evaluation.usecase.sectionconfig;

import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.evaluation.specialist.SectionEvaluationConfigDueDaysReader;
import com.red_escuela.backend.evaluation.specialist.SectionEvaluationConfigFinder;
import com.red_escuela.backend.evaluation.specialist.SectionEvaluationConfigInstructorAccessChecker;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetMySectionEvaluationConfigService implements GetMySectionEvaluationConfigUseCase {

    private final SectionEvaluationConfigFinder configFinder;
    private final SectionEvaluationConfigInstructorAccessChecker instructorAccess;

    private final SectionEvaluationConfigDueDaysReader dueDaysReader;

    public GetMySectionEvaluationConfigService(SectionEvaluationConfigFinder configFinder,
                                                SectionEvaluationConfigInstructorAccessChecker instructorAccess,
                                                SectionEvaluationConfigDueDaysReader dueDaysReader) {
        this.configFinder = configFinder;
        this.instructorAccess = instructorAccess;
        this.dueDaysReader = dueDaysReader;
    }

    @Override
    public SectionEvaluationConfig get(GetMySectionEvaluationConfigCommand command) {
        instructorAccess.requireAssignedToSection(command.sectionId(), command.instructorId());
        return configFinder.findOrThrow(command.sectionId());
    }

    @Override
    public int evaluationConfigDueDays() {
        return dueDaysReader.read();
    }
}
