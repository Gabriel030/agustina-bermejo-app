package com.red_escuela.backend.identity.dto;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.PreceptorAssignment;
import io.swagger.v3.oas.annotations.media.Schema;

public record PreceptorAssignmentResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long personId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String personName,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) Long subjectSectionId,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) Long courseSectionId) {

    public static PreceptorAssignmentResponse from(PreceptorAssignment assignment, Person person) {
        return new PreceptorAssignmentResponse(assignment.getId(), assignment.getPersonId(),
                person.getName() + " " + person.getLastname(),
                assignment.getSubjectSectionId(), assignment.getCourseSectionId());
    }
}
