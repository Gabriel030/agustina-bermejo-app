package com.red_escuela.backend.attendance.usecase.record;

import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.specialist.JustifyAttendanceTransaction;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class JustifyAttendanceService implements JustifyAttendanceUseCase {

    private final JustifyAttendanceTransaction justifyTransaction;

    public JustifyAttendanceService(JustifyAttendanceTransaction justifyTransaction) {
        this.justifyTransaction = justifyTransaction;
    }

    @Override
    public Attendance justify(JustifyAttendanceCommand command) {
        return justifyTransaction.justify(command.attendanceId(), command.reason(),
                command.attachmentUrl(), command.exceptionReason());
    }
}
