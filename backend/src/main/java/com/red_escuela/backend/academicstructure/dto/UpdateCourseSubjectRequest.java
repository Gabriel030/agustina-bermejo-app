package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.shared.validation.RequestLimits;
import com.red_escuela.backend.shared.validation.SelfValidatingRequest;
import com.red_escuela.backend.academicstructure.entity.SubjectRecurrence;

public record UpdateCourseSubjectRequest(
        String name, Integer sortOrder, Integer weeklyWorkload,
        SubjectRecurrence subjectRecurrence, String qualificationType) implements SelfValidatingRequest {
    public UpdateCourseSubjectRequest {
        RequestLimits.requireMaxLength("name", name, 255);
        RequestLimits.requireMaxLength("qualificationType", qualificationType, 255);
        RequestLimits.requireBetween("sortOrder", sortOrder, 0, 10000);
        RequestLimits.requireBetween("weeklyWorkload", weeklyWorkload, 0, 200);
    }
}
