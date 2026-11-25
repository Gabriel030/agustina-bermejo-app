package com.red_escuela.backend.evaluation.usecase.sectionconfig;

import com.red_escuela.backend.identity.api.InstitutionAcademicSettingsReader;
import com.red_escuela.backend.evaluation.repository.SectionEvaluationConfigRepository;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListMySectionEvaluationConfigsService implements ListMySectionEvaluationConfigsUseCase {

    private final SectionEvaluationConfigRepository configRepository;

    private final InstitutionAcademicSettingsReader academicSettingsReader;

    public ListMySectionEvaluationConfigsService(SectionEvaluationConfigRepository configRepository,
                                                  InstitutionAcademicSettingsReader academicSettingsReader) {
        this.configRepository = configRepository;
        this.academicSettingsReader = academicSettingsReader;
    }

    @Override
    public List<SectionEvaluationConfigWithDeadline> list(Long instructorId) {
        int dueDays = academicSettingsReader.read().evaluationConfigDueDays();
        return configRepository.findBySubjectSection_Instructors_InstructorId(instructorId).stream()
                .map(config -> SectionEvaluationConfigWithDeadline.of(config, dueDays)).toList();
    }
}
