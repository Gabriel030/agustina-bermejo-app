package com.red_escuela.backend.attendance.usecase.attendance;

import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.service.AttendanceProofRecorder;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class SubmitAttendanceProofService implements SubmitAttendanceProofUseCase {

    private final AttendanceProofRecorder attendanceProofRecorder;

    public SubmitAttendanceProofService(AttendanceProofRecorder attendanceProofRecorder) {
        this.attendanceProofRecorder = attendanceProofRecorder;
    }

    @Override
    public Attendance submit(SubmitAttendanceProofCommand command) {
        return attendanceProofRecorder.recordProof(command.attendanceId(), command.reason(), command.attachmentUrl());
    }
}
