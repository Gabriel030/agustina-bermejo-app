package com.red_escuela.backend.attendance.usecase.record;

import java.time.LocalDate;

public record ExportCourseSectionAttendanceCommand(
        Long sectionId, Long instructorIdIfDocente, String scope, String format, LocalDate classDate) {
}
