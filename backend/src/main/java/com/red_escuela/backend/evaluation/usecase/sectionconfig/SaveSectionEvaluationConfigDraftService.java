package com.red_escuela.backend.evaluation.usecase.sectionconfig;

import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.evaluation.specialist.SectionEvaluationConfigDraftSaver;
import com.red_escuela.backend.evaluation.specialist.SectionEvaluationConfigInstructorAccessChecker;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class SaveSectionEvaluationConfigDraftService implements SaveSectionEvaluationConfigDraftUseCase {

    private final SectionEvaluationConfigInstructorAccessChecker instructorAccess;
    private final SectionEvaluationConfigDraftSaver draftSaving;

    public SaveSectionEvaluationConfigDraftService(SectionEvaluationConfigInstructorAccessChecker instructorAccess,
                                                   SectionEvaluationConfigDraftSaver draftSaving) {
        this.instructorAccess = instructorAccess;
        this.draftSaving = draftSaving;
    }

    @Override
    public SectionEvaluationConfig save(SaveSectionEvaluationConfigDraftCommand command) {
        instructorAccess.requireLeadInstructor(command.sectionId(), command.instructorId());
        return draftSaving.saveDraft(command.sectionId(), command.request());
    }
}
