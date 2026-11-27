package com.red_escuela.backend.evaluation.usecase.sectionconfig;

import com.red_escuela.backend.evaluation.dto.SectionEvaluationConfigResponse;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfigStatus;
import com.red_escuela.backend.evaluation.repository.SectionEvaluationConfigRepository;
import com.red_escuela.backend.evaluation.specialist.SectionEvaluationConfigResponseAssembler;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListSectionEvaluationConfigsService implements ListSectionEvaluationConfigsUseCase {

    private final SectionEvaluationConfigRepository configRepository;
    private final SectionEvaluationConfigResponseAssembler responseAssembler;

    public ListSectionEvaluationConfigsService(SectionEvaluationConfigRepository configRepository,
                                               SectionEvaluationConfigResponseAssembler responseAssembler) {
        this.configRepository = configRepository;
        this.responseAssembler = responseAssembler;
    }

    @Override
    public List<SectionEvaluationConfigResponse> list(SectionEvaluationConfigStatus status) {
        return configRepository.findByStatusAndSubjectSectionIsNotNull(status != null ? status : SectionEvaluationConfigStatus.PENDING_APPROVAL).stream()
                .map(responseAssembler::assembleForStaff)
                .toList();
    }
}
