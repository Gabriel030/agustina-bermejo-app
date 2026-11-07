package com.red_escuela.backend.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectAttendanceChangeRequestRequest(@NotBlank @Size(min = 10, max = 500) String reason) {
}
