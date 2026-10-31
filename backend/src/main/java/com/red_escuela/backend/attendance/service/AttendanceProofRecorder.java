package com.red_escuela.backend.attendance.service;

import com.red_escuela.backend.attendance.api.AttendanceProofSubmitter;
import com.red_escuela.backend.attendance.entity.Attendance;
import org.springframework.stereotype.Service;

@Service
public class AttendanceProofRecorder {

    private final AttendanceProofSubmitter attendanceProofSubmitter;
    private final AttendanceFinder attendanceFinder;

    public AttendanceProofRecorder(AttendanceProofSubmitter attendanceProofSubmitter, AttendanceFinder attendanceFinder) {
        this.attendanceProofSubmitter = attendanceProofSubmitter;
        this.attendanceFinder = attendanceFinder;
    }

    public Attendance recordProof(Long attendanceId, String reason, String attachmentUrl) {
        attendanceProofSubmitter.submitProof(attendanceId, reason, attachmentUrl);
        return attendanceFinder.findOrThrow(attendanceId);
    }
}
