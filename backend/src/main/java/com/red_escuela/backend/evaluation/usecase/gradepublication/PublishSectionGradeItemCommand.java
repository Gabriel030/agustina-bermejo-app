package com.red_escuela.backend.evaluation.usecase.gradepublication;

import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.evaluation.entity.GradeItemType;

public record PublishSectionGradeItemCommand(
        SectionKind sectionKind, Long sectionId, Long instructorId, Long publisherPersonId,
        GradeItemType gradeType, Integer sequenceNumber) {
}
