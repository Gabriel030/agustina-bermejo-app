package com.red_escuela.backend.evaluation.usecase.gradepublication;

import com.red_escuela.backend.evaluation.dto.PublishedGradesSummary;
import com.red_escuela.backend.evaluation.service.SectionGradeItemPublisher;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class PublishSectionGradeItemService implements PublishSectionGradeItemUseCase {

    private final SectionGradeItemPublisher publisher;

    public PublishSectionGradeItemService(SectionGradeItemPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public PublishedGradesSummary publish(PublishSectionGradeItemCommand command) {
        return publisher.publish(
                command.sectionKind(), command.sectionId(), command.instructorId(), command.publisherPersonId(),
                command.gradeType(), command.sequenceNumber());
    }
}
