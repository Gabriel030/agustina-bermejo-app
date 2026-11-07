package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.attendance.events.AttendanceStatusChangedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class CourseworkConditionRecalculator {
    private final GradeItemRecorder gradeItemService;

    public CourseworkConditionRecalculator(GradeItemRecorder gradeItemService) {
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
