package com.red_escuela.backend.enrollment.api;

public record CourseSectionScheduleView(
        Long sectionEnrollmentId, String courseName, String sectionName, String courseSubjectName, String schedule,
        String instructorTitular) {
}
