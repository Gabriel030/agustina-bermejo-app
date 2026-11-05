package com.red_escuela.backend.evaluation.usecase.sectionevaluationconfig;

import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.evaluation.service.SectionEvaluationConfigDraftSaving;
import com.red_escuela.backend.evaluation.service.SectionEvaluationConfigInstructorAccess;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class SaveSectionEvaluationConfigDraftService implements SaveSectionEvaluationConfigDraftUseCase {

    private final SectionEvaluationConfigInstructorAccess instructorAccess;
    private final SectionEvaluationConfigDraftSaving draftSaving;

    public SaveSectionEvaluationConfigDraftService(SectionEvaluationConfigInstructorAccess instructorAccess,
                                                   SectionEvaluationConfigDraftSaving draftSaving) {
        this.instructorAccess = instructorAccess;
        this.draftSaving = draftSaving;
    }

    @Override
    public SectionEvaluationConfig save(SaveSectionEvaluationConfigDraftCommand command) {
        instructorAccess.requireLeadInstructor(command.sectionId(), command.instructorId());
        return draftSaving.saveDraft(command.sectionId(), command.request());
    }
}
