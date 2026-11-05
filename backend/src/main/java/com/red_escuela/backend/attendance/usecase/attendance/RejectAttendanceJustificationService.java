package com.red_escuela.backend.attendance.usecase.attendance;

import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.service.AttendanceJustificationRejector;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class RejectAttendanceJustificationService implements RejectAttendanceJustificationUseCase {

    private final AttendanceJustificationRejector attendanceJustificationRejector;

    public RejectAttendanceJustificationService(AttendanceJustificationRejector attendanceJustificationRejector) {
        this.attendanceJustificationRejector = attendanceJustificationRejector;
    }

    @Override
    public Attendance reject(RejectAttendanceJustificationCommand command) {
        return attendanceJustificationRejector.rejectPendingJustification(command.attendanceId(), command.reason());
    }
}
