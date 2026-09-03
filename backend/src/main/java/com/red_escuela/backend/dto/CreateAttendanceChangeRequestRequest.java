package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.AttendanceStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateAttendanceChangeRequestRequest(@NotNull AttendanceStatus requestedStatus, @NotBlank String reason) {
}
