package com.red_escuela.backend.evaluation.usecase.sectionconfig;

import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.evaluation.specialist.SectionEvaluationConfigFinder;
import com.red_escuela.backend.evaluation.specialist.SectionEvaluationConfigInstructorAccessChecker;
import com.red_escuela.backend.identity.api.InstitutionAcademicSettingsReader;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetMySectionEvaluationConfigService implements GetMySectionEvaluationConfigUseCase {

    private final SectionEvaluationConfigFinder configFinder;
    private final SectionEvaluationConfigInstructorAccessChecker instructorAccess;

    private final InstitutionAcademicSettingsReader academicSettingsReader;

    public GetMySectionEvaluationConfigService(SectionEvaluationConfigFinder configFinder,
                                                SectionEvaluationConfigInstructorAccessChecker instructorAccess,
                                                InstitutionAcademicSettingsReader academicSettingsReader) {
        this.configFinder = configFinder;
        this.instructorAccess = instructorAccess;
        this.academicSettingsReader = academicSettingsReader;
    }

    @Override
    public SectionEvaluationConfig get(GetMySectionEvaluationConfigCommand command) {
        instructorAccess.requireAssignedToSection(command.sectionId(), command.instructorId());
        return configFinder.findOrThrow(command.sectionId());
    }

    @Override
    public int evaluationConfigDueDays() {
        return academicSettingsReader.read().evaluationConfigDueDays();
    }
}
