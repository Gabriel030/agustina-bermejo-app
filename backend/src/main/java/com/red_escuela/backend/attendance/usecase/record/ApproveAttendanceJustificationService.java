package com.red_escuela.backend.attendance.usecase.record;

import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.specialist.ApproveAttendanceJustificationTransaction;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ApproveAttendanceJustificationService implements ApproveAttendanceJustificationUseCase {

    private final ApproveAttendanceJustificationTransaction approveTransaction;

    public ApproveAttendanceJustificationService(ApproveAttendanceJustificationTransaction approveTransaction) {
        this.approveTransaction = approveTransaction;
    }

    @Override
    public Attendance approve(Long attendanceId) {
        return approveTransaction.approve(attendanceId);
    }
}
