package com.red_escuela.backend.evaluation.usecase.gradepublication;

import com.red_escuela.backend.evaluation.dto.PublishedGradesSummary;

public interface PublishProgramSectionGradeItemUseCase {
    PublishedGradesSummary publish(PublishSectionGradeItemCommand command);
}
