package com.red_escuela.backend.attendance.usecase.changerequest;

import com.red_escuela.backend.attendance.entity.AttendanceChangeRequest;
import com.red_escuela.backend.attendance.specialist.RevertAttendanceChangeRequestDecisionTransaction;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class RevertAttendanceChangeRequestDecisionService implements RevertAttendanceChangeRequestDecisionUseCase {

    private final RevertAttendanceChangeRequestDecisionTransaction revertTransaction;

    public RevertAttendanceChangeRequestDecisionService(RevertAttendanceChangeRequestDecisionTransaction revertTransaction) {
        this.revertTransaction = revertTransaction;
    }

    @Override
    public AttendanceChangeRequest revert(RevertAttendanceChangeRequestDecisionCommand command) {
        return revertTransaction.revert(command.requestId(), command.reason());
    }
}
