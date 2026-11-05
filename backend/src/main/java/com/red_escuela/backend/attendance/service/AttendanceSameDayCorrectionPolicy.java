package com.red_escuela.backend.attendance.service;

import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.attendance.entity.Attendance;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;

@Service
public class AttendanceSameDayCorrectionPolicy {

    private final Clock clock;

    public AttendanceSameDayCorrectionPolicy(Clock clock) {
        this.clock = clock;
    }

    public boolean allowsCorrection(Attendance attendance, Instructor instructor) {
        return attendance.getId() != null
                && attendance.isRecordableByInstructor()
                && attendance.wasRecordedBy(instructor)
                && attendance.getClassDate().equals(LocalDate.now(clock));
    }
}
