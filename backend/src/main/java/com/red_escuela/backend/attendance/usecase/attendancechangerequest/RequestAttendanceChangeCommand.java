package com.red_escuela.backend.attendance.usecase.attendancechangerequest;

import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.attendance.dto.CreateAttendanceChangeRequestRequest;

public record RequestAttendanceChangeCommand(Long attendanceId, Instructor requestedBy, CreateAttendanceChangeRequestRequest body) {
}
