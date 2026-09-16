package com.red_escuela.backend.attendance.usecase.attendancechangerequest;

import com.red_escuela.backend.attendance.entity.AttendanceChangeRequest;
import com.red_escuela.backend.attendance.entity.AttendanceChangeRequestStatus;
import com.red_escuela.backend.attendance.repository.AttendanceChangeRequestRepository;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListAttendanceChangeRequestsByStatusService implements ListAttendanceChangeRequestsByStatusUseCase {

    private final AttendanceChangeRequestRepository attendanceChangeRequestRepository;

    public ListAttendanceChangeRequestsByStatusService(AttendanceChangeRequestRepository attendanceChangeRequestRepository) {
        this.attendanceChangeRequestRepository = attendanceChangeRequestRepository;
    }

    @Override
    public List<AttendanceChangeRequest> list(AttendanceChangeRequestStatus status) {
        return attendanceChangeRequestRepository.findByStatus(status);
    }
}
