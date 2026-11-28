package com.red_escuela.backend.attendance.usecase.changerequest;

import com.red_escuela.backend.attendance.dto.AttendanceChangeRequestResponse;
import com.red_escuela.backend.shared.pagination.PageResponse;

public interface ListResolvedAttendanceChangeRequestsUseCase {
    PageResponse<AttendanceChangeRequestResponse> list(ListResolvedAttendanceChangeRequestsCommand command);
}
