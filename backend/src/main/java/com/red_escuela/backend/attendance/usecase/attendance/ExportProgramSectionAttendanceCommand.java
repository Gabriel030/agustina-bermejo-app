package com.red_escuela.backend.attendance.usecase.attendance;

import java.time.LocalDate;

public record ExportProgramSectionAttendanceCommand(
        Long sectionId, Long instructorIdIfDocente, String scope, String format, LocalDate classDate) {
}
