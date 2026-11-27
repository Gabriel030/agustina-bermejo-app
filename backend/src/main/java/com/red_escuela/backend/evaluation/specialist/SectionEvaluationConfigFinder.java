package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.evaluation.domain.SectionRef;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.evaluation.exception.SectionEvaluationConfigNotFoundException;
import com.red_escuela.backend.evaluation.repository.SectionEvaluationConfigRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class SectionEvaluationConfigFinder {

    private final SectionEvaluationConfigRepository configRepository;

    public SectionEvaluationConfigFinder(SectionEvaluationConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    public SectionEvaluationConfig findOrThrow(SectionRef section) {
        Optional<SectionEvaluationConfig> config = section.isCourse()
                ? configRepository.findByCourseSectionId(section.id())
                : configRepository.findBySubjectSectionId(section.id());
        return config.orElseThrow(() -> new SectionEvaluationConfigNotFoundException(section.id()));
    }
}
