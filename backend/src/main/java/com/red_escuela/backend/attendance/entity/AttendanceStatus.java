package com.red_escuela.backend.attendance.entity;

import com.red_escuela.backend.attendance.exception.AttendanceStatusNotRecordableException;

public enum AttendanceStatus {
    PRESENT,
    ABSENT,
    LATE,
    JUSTIFICATION_PENDING,
    JUSTIFIED;

    public void requireRecordableByInstructor() {
        if (this != PRESENT && this != ABSENT && this != LATE) {
            throw new AttendanceStatusNotRecordableException(this);
        }
    }
}
