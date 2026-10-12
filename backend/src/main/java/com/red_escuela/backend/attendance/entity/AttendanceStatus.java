package com.red_escuela.backend.attendance.entity;

import com.red_escuela.backend.attendance.exception.AttendanceStatusNotRecordableException;

import java.util.Arrays;
import java.util.List;

public enum AttendanceStatus {
    PRESENT,
    ABSENT,
    LATE,
    JUSTIFICATION_PENDING,
    JUSTIFIED;

    private static final List<AttendanceStatus> ABSENCE_STATUSES =
            Arrays.stream(values()).filter(AttendanceStatus::countsAsAbsence).toList();
    private static final List<AttendanceStatus> PRESENCE_STATUSES =
            Arrays.stream(values()).filter(AttendanceStatus::countsAsPresence).toList();

    public boolean countsAsAbsence() {
        return this == ABSENT || this == JUSTIFICATION_PENDING;
    }

    public boolean countsAsPresence() {
        return this == PRESENT || this == LATE || this == JUSTIFIED;
    }

    public static List<AttendanceStatus> absenceStatuses() {
        return ABSENCE_STATUSES;
    }

    public static List<AttendanceStatus> presenceStatuses() {
        return PRESENCE_STATUSES;
    }

    public void requireRecordableByInstructor() {
        if (this != PRESENT && this != ABSENT && this != LATE) {
            throw new AttendanceStatusNotRecordableException(this);
        }
    }
}
