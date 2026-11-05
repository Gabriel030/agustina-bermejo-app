package com.red_escuela.backend.attendance.usecase.attendance;

import com.red_escuela.backend.attendance.dto.ExportedAttendanceFile;
import com.red_escuela.backend.attendance.service.AttendanceExportService;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ExportProgramSectionAttendanceService implements ExportProgramSectionAttendanceUseCase {
    private final AttendanceExportService exportService;

    public ExportProgramSectionAttendanceService(AttendanceExportService exportService) {
        this.exportService = exportService;
    }

    @Override
    public ExportedAttendanceFile export(ExportProgramSectionAttendanceCommand command) {
        return exportService.exportProgramSection(command.sectionId(), command.instructorIdIfDocente(),
                command.scope(), command.format(), command.classDate());
    }
}
