package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.AttendanceStatus;
import jakarta.validation.constraints.NotNull;

public record AttendanceEntry(@NotNull Long enrollmentId, @NotNull AttendanceStatus status) {
}
