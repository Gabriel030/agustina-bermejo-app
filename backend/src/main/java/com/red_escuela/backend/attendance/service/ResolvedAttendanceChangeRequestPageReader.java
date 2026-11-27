package com.red_escuela.backend.attendance.service;

import com.red_escuela.backend.attendance.domain.AttendanceChangeRequestResult;
import com.red_escuela.backend.attendance.dto.AttendanceChangeRequestResponse;
import com.red_escuela.backend.attendance.entity.AttendanceChangeRequest;
import com.red_escuela.backend.attendance.entity.AttendanceChangeRequestStatus;
import com.red_escuela.backend.attendance.repository.AttendanceChangeRequestRepository;
import com.red_escuela.backend.attendance.specialist.AttendanceChangeRequestResponseAssembler;
import com.red_escuela.backend.shared.pagination.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ResolvedAttendanceChangeRequestPageReader {

    private static final List<AttendanceChangeRequestStatus> RESOLVED_STATUSES =
            List.of(AttendanceChangeRequestStatus.APPROVED, AttendanceChangeRequestStatus.REJECTED);

    private final AttendanceChangeRequestRepository attendanceChangeRequestRepository;
    private final AttendanceChangeRequestResponseAssembler responseAssembler;

    public ResolvedAttendanceChangeRequestPageReader(AttendanceChangeRequestRepository attendanceChangeRequestRepository,
                                                     AttendanceChangeRequestResponseAssembler responseAssembler) {
        this.attendanceChangeRequestRepository = attendanceChangeRequestRepository;
        this.responseAssembler = responseAssembler;
    }

    @Transactional(readOnly = true)
    public PageResponse<AttendanceChangeRequestResponse> read(AttendanceChangeRequestResult result, int page, int size) {
        int boundedPage = Math.max(page, 0);
        int boundedSize = Math.min(Math.max(size, 1), PageResponse.MAX_SIZE);
        List<AttendanceChangeRequestStatus> statuses = result == null
                ? RESOLVED_STATUSES
                : List.of(AttendanceChangeRequestStatus.valueOf(result.name()));
        Sort newestDecisionFirst = Sort.by(Sort.Direction.DESC, "approvedAt").and(Sort.by(Sort.Direction.DESC, "id"));
        Page<AttendanceChangeRequest> found = attendanceChangeRequestRepository.findByStatusIn(
                statuses, PageRequest.of(boundedPage, boundedSize, newestDecisionFirst));
        return new PageResponse<>(
                found.getContent().stream().map(responseAssembler::assemble).toList(),
                boundedPage, boundedSize, found.getTotalElements());
    }
}
