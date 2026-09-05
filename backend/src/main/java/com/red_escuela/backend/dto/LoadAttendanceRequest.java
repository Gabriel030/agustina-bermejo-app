package com.red_escuela.backend.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record LoadAttendanceRequest(
        @NotNull LocalDate classDate, @NotEmpty List<AttendanceEntry> entries,
        @Size(max = 1000) String topic, @Size(max = 500) String observations) {
}
