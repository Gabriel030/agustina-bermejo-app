package com.red_escuela.backend.evaluation.usecase.sectionconfig;

import com.red_escuela.backend.evaluation.dto.SectionEvaluationConfigResponse;
import com.red_escuela.backend.evaluation.specialist.SectionEvaluationConfigDraftSaver;
import com.red_escuela.backend.evaluation.specialist.SectionEvaluationConfigInstructorAccessChecker;
import com.red_escuela.backend.evaluation.specialist.SectionEvaluationConfigResponseAssembler;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class SaveSectionEvaluationConfigDraftService implements SaveSectionEvaluationConfigDraftUseCase {

    private final SectionEvaluationConfigInstructorAccessChecker instructorAccess;
    private final SectionEvaluationConfigDraftSaver draftSaving;
    private final SectionEvaluationConfigResponseAssembler responseAssembler;

    public SaveSectionEvaluationConfigDraftService(SectionEvaluationConfigInstructorAccessChecker instructorAccess,
                                                   SectionEvaluationConfigDraftSaver draftSaving,
                                                   SectionEvaluationConfigResponseAssembler responseAssembler) {
        this.instructorAccess = instructorAccess;
        this.draftSaving = draftSaving;
        this.responseAssembler = responseAssembler;
    }

    @Override
    public SectionEvaluationConfigResponse save(SaveSectionEvaluationConfigDraftCommand command) {
        instructorAccess.requireLeadInstructor(command.sectionId(), command.instructorId());
        return responseAssembler.assembleForInstructor(
                draftSaving.saveDraft(command.sectionId(), command.request()), command.instructorId());
    }
}
