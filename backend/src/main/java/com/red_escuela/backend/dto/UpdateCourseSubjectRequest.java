package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.SubjectRecurrence;

public record UpdateCourseSubjectRequest(
        String name, Integer sortOrder, Integer weeklyWorkload,
        SubjectRecurrence subjectRecurrence, String qualificationType) {
}
