package com.red_escuela.backend.attendance.policy;

import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.attendance.entity.Attendance;

import java.time.LocalDate;

public class AttendanceSameDayCorrectionPolicy {

    public boolean allowsCorrection(Attendance attendance, Instructor instructor, LocalDate today) {
        return attendance.getId() != null
                && attendance.isRecordableByInstructor()
                && attendance.wasRecordedBy(instructor)
                && attendance.getClassDate().equals(today);
    }
}
