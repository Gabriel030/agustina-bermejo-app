package com.red_escuela.backend.attendance.dto;

import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import jakarta.validation.constraints.NotNull;

public record AttendanceEntry(@NotNull Long enrollmentId, @NotNull AttendanceStatus status) {
}
