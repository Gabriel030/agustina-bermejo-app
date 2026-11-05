package com.red_escuela.backend.evaluation.service;

import com.red_escuela.backend.evaluation.dto.UpdateSectionEvaluationConfigRequest;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.evaluation.exception.InvalidSectionEvaluationConfigTransitionException;
import com.red_escuela.backend.evaluation.repository.SectionEvaluationConfigRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SectionEvaluationConfigDraftSaving {

    private final SectionEvaluationConfigRepository configRepository;
    private final SectionEvaluationConfigFinder configFinder;
    private final SectionEvaluationConfigDateValidator dateValidator;

    public SectionEvaluationConfigDraftSaving(SectionEvaluationConfigRepository configRepository,
                                              SectionEvaluationConfigFinder configFinder,
                                              SectionEvaluationConfigDateValidator dateValidator) {
        this.configRepository = configRepository;
        this.configFinder = configFinder;
        this.dateValidator = dateValidator;
    }

    @Transactional
    public SectionEvaluationConfig saveDraft(Long sectionId, UpdateSectionEvaluationConfigRequest request) {
        SectionEvaluationConfig config = configFinder.findOrThrow(sectionId);
        if (!config.isEditable()) {
            throw new InvalidSectionEvaluationConfigTransitionException(config.getStatus(), "editar");
        }
        dateValidator.requireDatesWithinTermAndOffHolidays(config, request);

        config.setTpCount(request.tpCount());
        config.setTp1DueDate(request.tp1DueDate());
        config.setTp2DueDate(request.tp2DueDate());
        config.setTp3DueDate(request.tp3DueDate());
        config.setExam1Date(request.exam1Date());
        config.setExam2Date(request.exam2Date());
        config.setExam3Date(request.exam3Date());
        config.setExam4Date(request.exam4Date());
        config.setFinalCall1Date(request.finalCall1Date());
        config.setFinalCall2Date(request.finalCall2Date());
        return configRepository.save(config);
    }
}
