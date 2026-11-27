package com.red_escuela.backend.attendance.usecase.changerequest;

import com.red_escuela.backend.attendance.domain.AttendanceChangeRequestScope;
import com.red_escuela.backend.attendance.entity.AttendanceChangeRequest;
import com.red_escuela.backend.attendance.entity.AttendanceChangeRequestStatus;
import com.red_escuela.backend.attendance.repository.AttendanceChangeRequestRepository;
import com.red_escuela.backend.attendance.specialist.AttendanceChangeRequestScopeResolver;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.data.domain.Pageable;

import java.util.List;

@UseCase
public class ListAttendanceChangeRequestsByStatusService implements ListAttendanceChangeRequestsByStatusUseCase {

    private final AttendanceChangeRequestRepository attendanceChangeRequestRepository;
    private final AttendanceChangeRequestScopeResolver scopeResolver;

    public ListAttendanceChangeRequestsByStatusService(AttendanceChangeRequestRepository attendanceChangeRequestRepository,
                                                       AttendanceChangeRequestScopeResolver scopeResolver) {
        this.attendanceChangeRequestRepository = attendanceChangeRequestRepository;
        this.scopeResolver = scopeResolver;
    }

    @Override
    public List<AttendanceChangeRequest> list(AttendanceChangeRequestStatus status) {
        AttendanceChangeRequestScope scope = scopeResolver.currentScope();
        return attendanceChangeRequestRepository.findInScope(
                List.of(status), scope.unrestricted(), scope.subjectSectionIdsForQuery(), scope.courseSectionIdsForQuery(), scope.programCohortIdsForQuery(), Pageable.unpaged())
                .getContent();
    }
}
