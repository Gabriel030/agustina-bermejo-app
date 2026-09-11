package com.red_escuela.backend.attendance.usecase.attendancechangerequest;

import com.red_escuela.backend.attendance.entity.AttendanceChangeRequest;
import com.red_escuela.backend.attendance.repository.AttendanceChangeRequestRepository;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListMyAttendanceChangeRequestsService implements ListMyAttendanceChangeRequestsUseCase {

    private final AttendanceChangeRequestRepository attendanceChangeRequestRepository;

    public ListMyAttendanceChangeRequestsService(AttendanceChangeRequestRepository attendanceChangeRequestRepository) {
        this.attendanceChangeRequestRepository = attendanceChangeRequestRepository;
    }

    @Override
    public List<AttendanceChangeRequest> list(Long instructorId) {
        return attendanceChangeRequestRepository.findByRequestedByIdOrderByCreatedAtDesc(instructorId);
    }
}
