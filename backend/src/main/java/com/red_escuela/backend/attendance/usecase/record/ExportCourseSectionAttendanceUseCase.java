package com.red_escuela.backend.attendance.usecase.record;

import com.red_escuela.backend.attendance.dto.ExportedAttendanceFile;

public interface ExportCourseSectionAttendanceUseCase {
    ExportedAttendanceFile export(ExportCourseSectionAttendanceCommand command);
}
