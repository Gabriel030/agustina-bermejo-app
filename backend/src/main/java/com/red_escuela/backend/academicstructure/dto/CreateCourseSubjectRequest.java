package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.entity.SubjectRecurrence;
import jakarta.validation.constraints.NotBlank;

public record CreateCourseSubjectRequest(
        @NotBlank String name, Integer sortOrder, Integer weeklyWorkload,
        SubjectRecurrence subjectRecurrence, String qualificationType) {
}
