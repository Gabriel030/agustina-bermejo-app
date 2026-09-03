package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.SubjectSectionInstructor;

public record SectionInstructorSummary(String name, String role) {
    public static SectionInstructorSummary from(SubjectSectionInstructor instructor) {
        var person = instructor.getInstructor().getPerson();
        return new SectionInstructorSummary(person.getName() + " " + person.getLastname(), instructor.getRole().name());
    }
}
