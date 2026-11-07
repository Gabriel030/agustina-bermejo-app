package com.red_escuela.backend.attendance.usecase.record;

import com.red_escuela.backend.attendance.dto.ExportedAttendanceFile;
import com.red_escuela.backend.attendance.specialist.AttendanceExporter;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ExportProgramSectionAttendanceService implements ExportProgramSectionAttendanceUseCase {
    private final AttendanceExporter exportService;

    public ExportProgramSectionAttendanceService(AttendanceExporter exportService) {
        this.exportService = exportService;
    }

    @Override
    public ExportedAttendanceFile export(ExportProgramSectionAttendanceCommand command) {
        return exportService.exportProgramSection(command.sectionId(), command.instructorIdIfDocente(),
                command.scope(), command.format(), command.classDate());
    }
}
