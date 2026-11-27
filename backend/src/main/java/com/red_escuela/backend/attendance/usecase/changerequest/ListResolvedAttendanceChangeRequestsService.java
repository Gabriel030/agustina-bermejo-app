package com.red_escuela.backend.attendance.usecase.changerequest;

import com.red_escuela.backend.attendance.dto.AttendanceChangeRequestResponse;
import com.red_escuela.backend.attendance.service.ResolvedAttendanceChangeRequestPageReader;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.pagination.PageResponse;

@UseCase
public class ListResolvedAttendanceChangeRequestsService implements ListResolvedAttendanceChangeRequestsUseCase {

    private final ResolvedAttendanceChangeRequestPageReader pageReader;

    public ListResolvedAttendanceChangeRequestsService(ResolvedAttendanceChangeRequestPageReader pageReader) {
        this.pageReader = pageReader;
    }

    @Override
    public PageResponse<AttendanceChangeRequestResponse> list(ListResolvedAttendanceChangeRequestsCommand command) {
        return pageReader.read(command.result(), command.page(), command.size());
    }
}
