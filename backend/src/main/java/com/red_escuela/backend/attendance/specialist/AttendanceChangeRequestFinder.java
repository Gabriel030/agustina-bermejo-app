package com.red_escuela.backend.attendance.specialist;

import com.red_escuela.backend.attendance.domain.AttendanceChangeRequestScope;
import com.red_escuela.backend.attendance.entity.AttendanceChangeRequest;
import com.red_escuela.backend.attendance.entity.AttendanceChangeRequestStatus;
import com.red_escuela.backend.attendance.exception.AttendanceChangeRequestNotFoundException;
import com.red_escuela.backend.attendance.exception.InvalidAttendanceChangeRequestStateException;
import com.red_escuela.backend.attendance.repository.AttendanceChangeRequestRepository;
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
        AttendanceChangeRequestScope scope = scopeResolver.currentScope();
        return attendanceChangeRequestRepository.findByIdInScope(
                        id, scope.unrestricted(), scope.subjectSectionIdsForQuery(), scope.courseSectionIdsForQuery(), scope.programCohortIdsForQuery())
                .orElseThrow(() -> new AttendanceChangeRequestNotFoundException(id));
    }

    public void requirePending(AttendanceChangeRequest request) {
        if (request.getStatus() != AttendanceChangeRequestStatus.PENDING) {
            throw new InvalidAttendanceChangeRequestStateException(request.getId());
        }
    }
}
