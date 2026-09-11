package com.red_escuela.backend.attendance.service;

import com.red_escuela.backend.attendance.entity.AttendanceChangeRequest;
import com.red_escuela.backend.attendance.entity.AttendanceChangeRequestStatus;
import com.red_escuela.backend.attendance.exception.AttendanceChangeRequestNotFoundException;
import com.red_escuela.backend.attendance.exception.InvalidAttendanceChangeRequestStateException;
import com.red_escuela.backend.attendance.repository.AttendanceChangeRequestRepository;
import org.springframework.stereotype.Service;

@Service
public class AttendanceChangeRequestFinder {

    private final AttendanceChangeRequestRepository attendanceChangeRequestRepository;

    public AttendanceChangeRequestFinder(AttendanceChangeRequestRepository attendanceChangeRequestRepository) {
        this.attendanceChangeRequestRepository = attendanceChangeRequestRepository;
    }

    public AttendanceChangeRequest findOrThrow(Long id) {
        return attendanceChangeRequestRepository.findById(id)
                .orElseThrow(() -> new AttendanceChangeRequestNotFoundException(id));
    }

    public void requirePending(AttendanceChangeRequest request) {
        if (request.getStatus() != AttendanceChangeRequestStatus.PENDING) {
            throw new InvalidAttendanceChangeRequestStateException(request.getId());
        }
    }
}
