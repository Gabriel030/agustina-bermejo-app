package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.CourseSubject;

public record CourseSubjectResponse(
        Long id, Long courseId, String name, Integer sortOrder, Integer weeklyWorkload, String status) {

    public static CourseSubjectResponse from(CourseSubject courseSubject) {
        return new CourseSubjectResponse(
                courseSubject.getId(), courseSubject.getCourse().getId(), courseSubject.getName(),
                courseSubject.getSortOrder(), courseSubject.getWeeklyWorkload(),
                courseSubject.getStatus() == null ? null : courseSubject.getStatus().name());
    }
}
