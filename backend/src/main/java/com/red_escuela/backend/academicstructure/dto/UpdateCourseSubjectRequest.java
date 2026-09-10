package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.entity.SubjectRecurrence;

public record UpdateCourseSubjectRequest(
        String name, Integer sortOrder, Integer weeklyWorkload,
        SubjectRecurrence subjectRecurrence, String qualificationType) {
}
