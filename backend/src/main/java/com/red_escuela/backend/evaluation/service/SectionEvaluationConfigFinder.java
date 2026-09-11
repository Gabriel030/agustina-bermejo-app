package com.red_escuela.backend.evaluation.service;

import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.evaluation.exception.SectionEvaluationConfigNotFoundException;
import com.red_escuela.backend.evaluation.repository.SectionEvaluationConfigRepository;
import org.springframework.stereotype.Service;

@Service
public class SectionEvaluationConfigFinder {

    private final SectionEvaluationConfigRepository configRepository;

    public SectionEvaluationConfigFinder(SectionEvaluationConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    public SectionEvaluationConfig findOrThrow(Long sectionId) {
        return configRepository.findBySubjectSectionId(sectionId)
                .orElseThrow(() -> new SectionEvaluationConfigNotFoundException(sectionId));
    }
}
