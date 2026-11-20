package com.red_escuela.backend.academicrecord.dto;

import com.red_escuela.backend.enrollment.api.CourseEnrollmentDetailView;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(name = "MyCourseEnrollmentDetailResponse")
public record MyCourseEnrollmentDetailResponse(
        Long id, Long courseId, String courseName, String status, LocalDateTime enrollmentDate,
        @Schema(nullable = true) LocalDateTime completionDate, List<Subject> subjects) {

    public static MyCourseEnrollmentDetailResponse from(CourseEnrollmentDetailView view, List<Subject> subjects) {
        return new MyCourseEnrollmentDetailResponse(
                view.id(), view.courseId(), view.courseName(), view.status(), view.enrollmentDate(),
                view.completionDate(), subjects);
    }

    @Schema(name = "MyCourseEnrollmentDetailSubjectResponse")
    public record Subject(
            StudentCourseSectionEnrollmentResponse sectionEnrollment, StudentAttendanceRiskResponse attendanceRisk,
            List<StudentAttendanceResponse> attendance, List<StudentGradeItemResponse> gradeItems) {
    }
}
