package com.red_escuela.backend.attendance.usecase.changerequest;

import com.red_escuela.backend.attendance.dto.AttendanceChangeRequestResponse;
import com.red_escuela.backend.attendance.specialist.RevertAttendanceChangeRequestDecisionTransaction;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class RevertAttendanceChangeRequestDecisionService implements RevertAttendanceChangeRequestDecisionUseCase {

    private final RevertAttendanceChangeRequestDecisionTransaction revertTransaction;

    public RevertAttendanceChangeRequestDecisionService(RevertAttendanceChangeRequestDecisionTransaction revertTransaction) {
        this.revertTransaction = revertTransaction;
    }

    @Override
    public AttendanceChangeRequestResponse revert(RevertAttendanceChangeRequestDecisionCommand command) {
        return revertTransaction.revert(command.requestId(), command.reason());
    }
}
