package com.red_escuela.backend.evaluation.usecase.gradepublication;

import com.red_escuela.backend.evaluation.dto.PublishGradeItemRequest;

public record PublishSectionGradeItemCommand(
        Long sectionId, Long instructorId, Long publisherPersonId, PublishGradeItemRequest request) {
}
