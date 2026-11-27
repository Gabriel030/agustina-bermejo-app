package com.red_escuela.backend.academicstructure.service;

import java.sql.SQLException;

final class ScheduleExclusionViolation {

    static final String EXCLUSION_VIOLATION_STATE = "23P01";
    static final String CLASSROOM_OVERLAP_CONSTRAINT = "ex_schedule_slot_classroom_overlap";
    static final String INSTRUCTOR_OVERLAP_CONSTRAINT = "ex_schedule_slot_instructor_overlap";

    private ScheduleExclusionViolation() {
    }

    static boolean isClassroomOverlap(Throwable failure) {
        return isExclusionOn(failure, CLASSROOM_OVERLAP_CONSTRAINT);
    }

    static boolean isInstructorOverlap(Throwable failure) {
        return isExclusionOn(failure, INSTRUCTOR_OVERLAP_CONSTRAINT);
    }

    private static boolean isExclusionOn(Throwable failure, String constraint) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            if (cause instanceof SQLException sql && EXCLUSION_VIOLATION_STATE.equals(sql.getSQLState())) {
                return cause.getMessage() != null && cause.getMessage().contains("\"" + constraint + "\"");
            }
        }
        return false;
    }
}
