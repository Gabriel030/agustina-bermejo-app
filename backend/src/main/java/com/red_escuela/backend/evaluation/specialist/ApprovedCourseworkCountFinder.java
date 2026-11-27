package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfigStatus;
import com.red_escuela.backend.evaluation.repository.SectionEvaluationConfigRepository;
import org.springframework.stereotype.Component;

@Component
public class ApprovedCourseworkCountFinder {

    private final SectionEvaluationConfigRepository configRepository;

    public ApprovedCourseworkCountFinder(SectionEvaluationConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    public int findExpectedCount(SectionKind kind, Long sectionId) {
        if (kind != SectionKind.PROGRAM) {
            return 0;
        }
        return configRepository.findBySubjectSectionId(sectionId)
                .filter(config -> config.getStatus() == SectionEvaluationConfigStatus.APPROVED)
                .map(SectionEvaluationConfig::getTpCount)
                .orElse(0);
    }
}
