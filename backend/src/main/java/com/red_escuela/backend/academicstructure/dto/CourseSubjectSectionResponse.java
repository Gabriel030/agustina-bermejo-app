package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;

public record CourseSubjectSectionResponse(
        Long id, String name, Long courseSubjectId, Long termId,
        String schedule, Integer capacity, String status) {

    public static CourseSubjectSectionResponse from(CourseSubjectSection section) {
        return new CourseSubjectSectionResponse(
                section.getId(), section.getName(), section.getCourseSubject().getId(), section.getTerm().getId(),
                section.getSchedule(), section.getCapacity(),
                section.getStatus() == null ? null : section.getStatus().name());
    }
}
