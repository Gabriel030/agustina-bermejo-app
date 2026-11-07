package com.red_escuela.backend.attendance.usecase.changerequest;

import com.red_escuela.backend.attendance.entity.AttendanceChangeRequest;
import com.red_escuela.backend.attendance.specialist.AttendanceChangeRequestSubmitter;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class RequestAttendanceChangeService implements RequestAttendanceChangeUseCase {

    private final AttendanceChangeRequestSubmitter changeRequestSubmission;

    public RequestAttendanceChangeService(AttendanceChangeRequestSubmitter changeRequestSubmission) {
        this.changeRequestSubmission = changeRequestSubmission;
    }

    @Override
    public AttendanceChangeRequest request(RequestAttendanceChangeCommand command) {
        return changeRequestSubmission.submit(command.attendanceId(), command.requestedById(), command.body());
    }
}
