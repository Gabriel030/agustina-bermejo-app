package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.evaluation.dto.SectionEvaluationConfigResponse;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfigStatus;
import com.red_escuela.backend.evaluation.repository.SectionEvaluationConfigRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CourseSectionEvaluationConfigStatusLister {

    private final SectionEvaluationConfigRepository configRepository;
    private final SectionEvaluationConfigResponseAssembler responseAssembler;

    public CourseSectionEvaluationConfigStatusLister(SectionEvaluationConfigRepository configRepository,
                                                     SectionEvaluationConfigResponseAssembler responseAssembler) {
        this.configRepository = configRepository;
        this.responseAssembler = responseAssembler;
    }

    public List<SectionEvaluationConfigResponse> listByStatusName(String statusName) {
        SectionEvaluationConfigStatus status = statusName == null
                ? SectionEvaluationConfigStatus.PENDING_APPROVAL
                : SectionEvaluationConfigStatus.valueOf(statusName);
        return configRepository.findByStatusAndCourseSectionIsNotNull(status).stream()
                .map(responseAssembler::assembleForStaff)
                .toList();
    }
}
