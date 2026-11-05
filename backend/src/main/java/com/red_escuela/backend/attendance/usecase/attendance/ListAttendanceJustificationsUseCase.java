package com.red_escuela.backend.attendance.usecase.attendance;

import com.red_escuela.backend.attendance.dto.AttendanceJustificationPage;
import com.red_escuela.backend.attendance.domain.AttendanceJustificationStatus;

public interface ListAttendanceJustificationsUseCase {
    AttendanceJustificationPage list(AttendanceJustificationStatus status, int page, int size);
}
