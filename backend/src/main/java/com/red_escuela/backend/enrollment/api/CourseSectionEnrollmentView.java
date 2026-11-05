package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.shared.domain.Grade;

import java.time.LocalDateTime;

public record CourseSectionEnrollmentView(
        Long id, Long courseEnrollmentId, Long courseId, String courseName, Long sectionId, String sectionName,
        Long courseSubjectId, String courseSubjectName, String status, Integer attemptNumber, Grade finalScore,
        String finalGradeType, LocalDateTime enrolledAt, LocalDateTime droppedAt, String schedule,
        String instructorTitular, String instructorAdjunto) {
}
