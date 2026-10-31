package com.red_escuela.backend.academicrecord.dto;

import com.red_escuela.backend.enrollment.api.CourseSectionEnrollmentView;
import com.red_escuela.backend.shared.domain.Grade;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(name = "MyCourseSectionEnrollmentResponse")
public record StudentCourseSectionEnrollmentResponse(
        Long id, Long courseEnrollmentId, Long courseId, String courseName, Long sectionId, String sectionName,
        Long courseSubjectId, String courseSubjectName, String status, Integer attemptNumber, Grade finalScore,
        String finalGradeType, LocalDateTime enrolledAt, LocalDateTime droppedAt, String schedule,
        String instructorTitular, String instructorAdjunto) {

    public static StudentCourseSectionEnrollmentResponse from(CourseSectionEnrollmentView view) {
        return new StudentCourseSectionEnrollmentResponse(
                view.id(), view.courseEnrollmentId(), view.courseId(), view.courseName(), view.sectionId(),
                view.sectionName(), view.courseSubjectId(), view.courseSubjectName(), view.status(),
                view.attemptNumber(), view.finalScore(), view.finalGradeType(), view.enrolledAt(),
                view.droppedAt(), view.schedule(), view.instructorTitular(), view.instructorAdjunto());
    }
}
