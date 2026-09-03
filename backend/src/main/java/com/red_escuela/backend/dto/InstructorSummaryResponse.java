package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.Instructor;

public record InstructorSummaryResponse(Long id, String name) {

    public static InstructorSummaryResponse from(Instructor instructor) {
        var person = instructor.getPerson();
        return new InstructorSummaryResponse(instructor.getId(), person.getName() + " " + person.getLastname());
    }
}
