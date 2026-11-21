package com.red_escuela.backend.academicrecord.usecase;

import java.time.LocalDate;

public record ExportMyCalendarCommand(Long studentId, LocalDate from, LocalDate to) {
}
