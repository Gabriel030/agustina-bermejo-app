package com.red_escuela.backend.attendance.usecase.attendance;

import com.red_escuela.backend.attendance.dto.AttendanceJustificationPage;
import com.red_escuela.backend.attendance.domain.AttendanceJustificationStatus;
import com.red_escuela.backend.attendance.service.AttendanceJustificationLister;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ListAttendanceJustificationsService implements ListAttendanceJustificationsUseCase {

    private final AttendanceJustificationLister attendanceJustificationLister;

    public ListAttendanceJustificationsService(AttendanceJustificationLister attendanceJustificationLister) {
        this.attendanceJustificationLister = attendanceJustificationLister;
    }

    @Override
    public AttendanceJustificationPage list(AttendanceJustificationStatus status, int page, int size) {
        return attendanceJustificationLister.listByStatus(status, page, size);
    }
}
