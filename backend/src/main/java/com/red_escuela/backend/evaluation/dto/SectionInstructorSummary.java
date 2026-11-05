package com.red_escuela.backend.evaluation.dto;

import com.red_escuela.backend.academicstructure.api.SectionInstructorView;

public record SectionInstructorSummary(String name, String role) {
    public static SectionInstructorSummary from(SectionInstructorView instructor) {
        return new SectionInstructorSummary(instructor.fullName(), instructor.role().name());
    }
}
