package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.SubjectRecurrence;
import jakarta.validation.constraints.NotBlank;

public record CreateCourseSubjectRequest(
        @NotBlank String name, Integer sortOrder, Integer weeklyWorkload,
        SubjectRecurrence subjectRecurrence, String qualificationType) {
}
