package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.CourseSubjectSection;
import com.red_escuela.backend.entity.SubjectSection;

import java.time.LocalDateTime;

public record TeachingSectionResponse(Long id, String type, String subjectName, String sectionName, LocalDateTime termStartDate) {

    public static TeachingSectionResponse fromSubjectSection(SubjectSection section) {
        return new TeachingSectionResponse(
                section.getId(), "PROGRAM", section.getSubject().getName(), section.getName(),
                section.getTerm() != null ? section.getTerm().getStartDate() : null);
    }

    public static TeachingSectionResponse fromCourseSubjectSection(CourseSubjectSection section) {
        return new TeachingSectionResponse(
                section.getId(), "COURSE", section.getCourseSubject().getName(), section.getName(),
                section.getTerm() != null ? section.getTerm().getStartDate() : null);
    }
}
