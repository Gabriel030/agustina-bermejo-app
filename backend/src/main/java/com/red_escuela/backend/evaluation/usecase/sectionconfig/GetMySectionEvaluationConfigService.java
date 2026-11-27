package com.red_escuela.backend.evaluation.usecase.sectionconfig;

import com.red_escuela.backend.evaluation.dto.SectionEvaluationConfigResponse;
import com.red_escuela.backend.evaluation.specialist.SectionEvaluationConfigDueDaysReader;
import com.red_escuela.backend.evaluation.specialist.SectionEvaluationConfigFinder;
import com.red_escuela.backend.evaluation.specialist.SectionEvaluationConfigInstructorAccessChecker;
import com.red_escuela.backend.evaluation.specialist.SectionEvaluationConfigResponseAssembler;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetMySectionEvaluationConfigService implements GetMySectionEvaluationConfigUseCase {

    private final SectionEvaluationConfigFinder configFinder;
    private final SectionEvaluationConfigInstructorAccessChecker instructorAccess;
    private final SectionEvaluationConfigDueDaysReader dueDaysReader;
    private final SectionEvaluationConfigResponseAssembler responseAssembler;

    public GetMySectionEvaluationConfigService(SectionEvaluationConfigFinder configFinder,
                                                SectionEvaluationConfigInstructorAccessChecker instructorAccess,
                                                SectionEvaluationConfigDueDaysReader dueDaysReader,
                                                SectionEvaluationConfigResponseAssembler responseAssembler) {
        this.configFinder = configFinder;
        this.instructorAccess = instructorAccess;
        this.dueDaysReader = dueDaysReader;
        this.responseAssembler = responseAssembler;
    }

    @Override
    public SectionEvaluationConfigResponse get(GetMySectionEvaluationConfigCommand command) {
        instructorAccess.requireAssignedToSection(command.sectionId(), command.instructorId());
        return responseAssembler.assembleForInstructor(configFinder.findOrThrow(command.sectionId()), command.instructorId())
                .withEvaluationConfigDueDays(dueDaysReader.read());
    }
}
