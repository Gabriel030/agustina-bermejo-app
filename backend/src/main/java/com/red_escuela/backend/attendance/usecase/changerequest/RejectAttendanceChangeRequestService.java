package com.red_escuela.backend.attendance.usecase.changerequest;

import com.red_escuela.backend.attendance.entity.AttendanceChangeRequest;
import com.red_escuela.backend.attendance.specialist.RejectAttendanceChangeRequestTransaction;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class RejectAttendanceChangeRequestService implements RejectAttendanceChangeRequestUseCase {

    private final RejectAttendanceChangeRequestTransaction rejectTransaction;

    public RejectAttendanceChangeRequestService(RejectAttendanceChangeRequestTransaction rejectTransaction) {
        this.rejectTransaction = rejectTransaction;
    }

    @Override
    public AttendanceChangeRequest reject(RejectAttendanceChangeRequestCommand command) {
        return rejectTransaction.reject(command.requestId(), command.reason());
    }
}
