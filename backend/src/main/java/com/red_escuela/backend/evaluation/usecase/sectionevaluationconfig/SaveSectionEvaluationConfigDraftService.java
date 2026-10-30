package com.red_escuela.backend.evaluation.usecase.sectionevaluationconfig;

import com.red_escuela.backend.evaluation.dto.UpdateSectionEvaluationConfigRequest;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.evaluation.exception.InvalidSectionEvaluationConfigTransitionException;
import com.red_escuela.backend.evaluation.repository.SectionEvaluationConfigRepository;
import com.red_escuela.backend.evaluation.service.SectionEvaluationConfigDateValidator;
import com.red_escuela.backend.evaluation.service.SectionEvaluationConfigFinder;
import com.red_escuela.backend.evaluation.service.SectionEvaluationConfigInstructorAccess;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class SaveSectionEvaluationConfigDraftService implements SaveSectionEvaluationConfigDraftUseCase {

    private final SectionEvaluationConfigRepository configRepository;
    private final SectionEvaluationConfigFinder configFinder;
    private final SectionEvaluationConfigInstructorAccess instructorAccess;
    private final SectionEvaluationConfigDateValidator dateValidator;

    public SaveSectionEvaluationConfigDraftService(SectionEvaluationConfigRepository configRepository,
                                                     SectionEvaluationConfigFinder configFinder,
                                                     SectionEvaluationConfigInstructorAccess instructorAccess,
                                                     SectionEvaluationConfigDateValidator dateValidator) {
        this.configRepository = configRepository;
        this.configFinder = configFinder;
        this.instructorAccess = instructorAccess;
        this.dateValidator = dateValidator;
    }

    @Override
    @Transactional
    public SectionEvaluationConfig save(SaveSectionEvaluationConfigDraftCommand command) {
        instructorAccess.requireLeadInstructor(command.sectionId(), command.instructorId());
        SectionEvaluationConfig config = configFinder.findOrThrow(command.sectionId());
        if (!config.isEditable()) {
            throw new InvalidSectionEvaluationConfigTransitionException(config.getStatus(), "editar");
        }
        UpdateSectionEvaluationConfigRequest request = command.request();
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
