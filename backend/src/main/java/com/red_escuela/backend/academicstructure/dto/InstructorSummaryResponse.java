package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.entity.Instructor;
import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record InstructorSummaryResponse(Long id, String name) {

    public static InstructorSummaryResponse from(Instructor instructor) {
        var person = instructor.getPerson();
        return new InstructorSummaryResponse(instructor.getId(), person.getName() + " " + person.getLastname());
    }
}
