package com.red_escuela.backend.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record CargarAsistenciaRequest(@NotNull LocalDate classDate, @NotEmpty List<AttendanceEntry> entries) {
}
