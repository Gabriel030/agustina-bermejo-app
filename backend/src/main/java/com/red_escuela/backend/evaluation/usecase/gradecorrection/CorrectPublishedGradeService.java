package com.red_escuela.backend.evaluation.usecase.gradecorrection;

import com.red_escuela.backend.evaluation.entity.GradeItemAudit;
import com.red_escuela.backend.evaluation.service.PublishedGradeCorrector;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class CorrectPublishedGradeService implements CorrectPublishedGradeUseCase {

    private final PublishedGradeCorrector corrector;

    public CorrectPublishedGradeService(PublishedGradeCorrector corrector) {
        this.corrector = corrector;
    }

    @Override
    public GradeItemAudit correct(CorrectPublishedGradeCommand command) {
        return corrector.correct(command.gradeItemId(), command.request(), command.actorPersonId());
    }
}
