package com.red_escuela.backend.evaluation.service;

import com.red_escuela.backend.attendance.service.AttendanceStatusChangedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class CourseworkConditionRecalculator {
    private final GradeItemService gradeItemService;

    public CourseworkConditionRecalculator(GradeItemService gradeItemService) {
        this.gradeItemService = gradeItemService;
    }

    @EventListener
    public void onAttendanceStatusChanged(AttendanceStatusChangedEvent event) {
        if (event.programSectionEnrollmentId() != null) {
            gradeItemService.recalculateProgramEnrollmentCondition(event.programSectionEnrollmentId());
        }
        if (event.courseSectionEnrollmentId() != null) {
            gradeItemService.recalculateCourseEnrollmentCondition(event.courseSectionEnrollmentId());
        }
    }
}
