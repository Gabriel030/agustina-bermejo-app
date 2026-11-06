package com.red_escuela.backend.evaluation.usecase.gradepublication;

import com.red_escuela.backend.evaluation.dto.PublishedGradesSummary;
import com.red_escuela.backend.evaluation.service.SectionGradeItemPublisher;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class PublishProgramSectionGradeItemService implements PublishProgramSectionGradeItemUseCase {

    private final SectionGradeItemPublisher publisher;

    public PublishProgramSectionGradeItemService(SectionGradeItemPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public PublishedGradesSummary publish(PublishSectionGradeItemCommand command) {
        return publisher.publishProgramSection(
                command.sectionId(), command.instructorId(), command.publisherPersonId(), command.request());
    }
}
