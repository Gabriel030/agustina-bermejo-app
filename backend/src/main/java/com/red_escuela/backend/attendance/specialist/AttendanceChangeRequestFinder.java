package com.red_escuela.backend.attendance.specialist;

import com.red_escuela.backend.attendance.entity.AttendanceChangeRequest;
import com.red_escuela.backend.attendance.entity.AttendanceChangeRequestStatus;
import com.red_escuela.backend.attendance.exception.AttendanceChangeRequestNotFoundException;
import com.red_escuela.backend.attendance.exception.InvalidAttendanceChangeRequestStateException;
import com.red_escuela.backend.attendance.repository.AttendanceChangeRequestRepository;
import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.service.AttendanceChangeRequestScopeResolver;
import org.springframework.stereotype.Service;

@Service
public class AttendanceChangeRequestFinder {

    private final AttendanceChangeRequestRepository attendanceChangeRequestRepository;

    private final AttendanceChangeRequestScopeResolver scopeResolver;

    public AttendanceChangeRequestFinder(AttendanceChangeRequestRepository attendanceChangeRequestRepository,
                                         AttendanceChangeRequestScopeResolver scopeResolver) {
        this.attendanceChangeRequestRepository = attendanceChangeRequestRepository;
        this.scopeResolver = scopeResolver;
    }

    public AttendanceChangeRequest findOrThrow(Long id) {
        AttendanceChangeRequest request = attendanceChangeRequestRepository.findById(id)
                .orElseThrow(() -> new AttendanceChangeRequestNotFoundException(id));
        Attendance attendance = request.getAttendance();
        Long programId = attendance.getProgramSubjectSectionEnrollment() == null ? null
                : attendance.getProgramSubjectSectionEnrollment().getProgramEnrollment().getProgram().getId();
        Long courseId = attendance.getCourseSubjectSectionEnrollment() == null ? null
                : attendance.getCourseSubjectSectionEnrollment().getCourseEnrollment().getCourse().getId();
        if (!scopeResolver.currentScope().covers(programId, courseId)) {
            throw new AttendanceChangeRequestNotFoundException(id);
        }
        return request;
    }

    public void requirePending(AttendanceChangeRequest request) {
        if (request.getStatus() != AttendanceChangeRequestStatus.PENDING) {
            throw new InvalidAttendanceChangeRequestStateException(request.getId());
        }
    }
}
