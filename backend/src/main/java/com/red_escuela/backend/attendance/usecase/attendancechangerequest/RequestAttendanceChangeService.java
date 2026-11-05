package com.red_escuela.backend.attendance.usecase.attendancechangerequest;

import com.red_escuela.backend.attendance.entity.AttendanceChangeRequest;
import com.red_escuela.backend.attendance.service.AttendanceChangeRequestSubmission;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class RequestAttendanceChangeService implements RequestAttendanceChangeUseCase {

    private final AttendanceChangeRequestSubmission changeRequestSubmission;

    public RequestAttendanceChangeService(AttendanceChangeRequestSubmission changeRequestSubmission) {
        this.changeRequestSubmission = changeRequestSubmission;
    }

    @Override
    public AttendanceChangeRequest request(RequestAttendanceChangeCommand command) {
        return changeRequestSubmission.submit(command.attendanceId(), command.requestedById(), command.body());
    }
}
