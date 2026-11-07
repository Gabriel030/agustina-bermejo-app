package com.red_escuela.backend.academicrecord.dto;

import com.red_escuela.backend.enrollment.api.CourseSectionScheduleView;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "MyScheduleItemResponse")
public record MyScheduleItemResponse(
        Long sectionEnrollmentId, String courseName, String sectionName, String courseSubjectName, @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) String schedule,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) String instructorTitular) {

    public static MyScheduleItemResponse from(CourseSectionScheduleView view) {
        return new MyScheduleItemResponse(
                view.sectionEnrollmentId(), view.courseName(), view.sectionName(), view.courseSubjectName(),
                view.schedule(), view.instructorTitular());
    }
}
