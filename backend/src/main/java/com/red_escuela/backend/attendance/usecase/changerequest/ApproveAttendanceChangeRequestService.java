package com.red_escuela.backend.attendance.usecase.changerequest;

import com.red_escuela.backend.attendance.entity.AttendanceChangeRequest;
import com.red_escuela.backend.attendance.specialist.ApproveAttendanceChangeRequestTransaction;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ApproveAttendanceChangeRequestService implements ApproveAttendanceChangeRequestUseCase {

    private final ApproveAttendanceChangeRequestTransaction approveTransaction;

    public ApproveAttendanceChangeRequestService(ApproveAttendanceChangeRequestTransaction approveTransaction) {
        this.approveTransaction = approveTransaction;
    }

    @Override
    public AttendanceChangeRequest approve(Long id) {
        return approveTransaction.approve(id);
    }
}
