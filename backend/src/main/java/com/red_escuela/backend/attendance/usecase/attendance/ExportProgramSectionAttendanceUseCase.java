package com.red_escuela.backend.attendance.usecase.attendance;

import com.red_escuela.backend.attendance.dto.ExportedAttendanceFile;

public interface ExportProgramSectionAttendanceUseCase {
    ExportedAttendanceFile export(ExportProgramSectionAttendanceCommand command);
}
