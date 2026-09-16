package com.red_escuela.backend.evaluation.usecase.sectionevaluationconfig;

import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfigStatus;
import com.red_escuela.backend.evaluation.repository.SectionEvaluationConfigRepository;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListSectionEvaluationConfigsService implements ListSectionEvaluationConfigsUseCase {

    private final SectionEvaluationConfigRepository configRepository;

    public ListSectionEvaluationConfigsService(SectionEvaluationConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    @Override
    public List<SectionEvaluationConfig> list(SectionEvaluationConfigStatus status) {
        return configRepository.findByStatus(status != null ? status : SectionEvaluationConfigStatus.PENDING_APPROVAL);
    }
}
