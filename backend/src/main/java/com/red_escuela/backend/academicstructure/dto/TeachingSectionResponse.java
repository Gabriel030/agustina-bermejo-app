package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;

import java.time.LocalDateTime;

public record TeachingSectionResponse(Long id, String type, String subjectName, String sectionName,
                                      LocalDateTime termStartDate, LocalDateTime termEndDate) {

    public static TeachingSectionResponse fromSubjectSection(SubjectSection section) {
        return new TeachingSectionResponse(
                section.getId(), "PROGRAM", section.getSubject().getName(), section.getName(),
                section.getTerm() != null ? section.getTerm().getStartDate() : null,
                section.getTerm() != null ? section.getTerm().getEndDate() : null);
    }

    public static TeachingSectionResponse fromCourseSubjectSection(CourseSubjectSection section) {
        return new TeachingSectionResponse(
                section.getId(), "COURSE", section.getCourseSubject().getName(), section.getName(),
                section.getTerm() != null ? section.getTerm().getStartDate() : null,
                section.getTerm() != null ? section.getTerm().getEndDate() : null);
    }
}
