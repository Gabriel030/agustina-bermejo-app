package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.shared.validation.RequestLimits;
import com.red_escuela.backend.shared.validation.SelfValidatingRequest;
import com.red_escuela.backend.academicstructure.entity.SubjectRecurrence;
import jakarta.validation.constraints.NotBlank;

public record CreateCourseSubjectRequest(
        @NotBlank String name, Integer sortOrder, Integer weeklyWorkload,
        SubjectRecurrence subjectRecurrence, String qualificationType) implements SelfValidatingRequest {
    public CreateCourseSubjectRequest {
        RequestLimits.requireMaxLength("name", name, 255);
        RequestLimits.requireMaxLength("qualificationType", qualificationType, 255);
        RequestLimits.requireAtMost("sortOrder", sortOrder, 10000);
        RequestLimits.requireAtMost("weeklyWorkload", weeklyWorkload, 200);
    }
}
