package com.red_escuela.backend.evaluation.usecase.sectionevaluationconfig;

import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.evaluation.repository.SectionEvaluationConfigRepository;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListMySectionEvaluationConfigsService implements ListMySectionEvaluationConfigsUseCase {

    private final SectionEvaluationConfigRepository configRepository;

    public ListMySectionEvaluationConfigsService(SectionEvaluationConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    @Override
    public List<SectionEvaluationConfig> list(Long instructorId) {
        return configRepository.findBySubjectSection_Instructors_InstructorId(instructorId);
    }
}
