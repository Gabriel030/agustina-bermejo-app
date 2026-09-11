package com.red_escuela.backend.attendance.dto;

import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateAttendanceChangeRequestRequest(@NotNull AttendanceStatus requestedStatus, @NotBlank String reason) {
}
