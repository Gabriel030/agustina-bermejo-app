package com.red_escuela.backend.attendance.usecase.attendance;

import com.red_escuela.backend.attendance.dto.ExportedAttendanceFile;
import com.red_escuela.backend.attendance.service.AttendanceExportService;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ExportCourseSectionAttendanceService implements ExportCourseSectionAttendanceUseCase {
    private final AttendanceExportService exportService;

    public ExportCourseSectionAttendanceService(AttendanceExportService exportService) {
        this.exportService = exportService;
    }

    @Override
    public ExportedAttendanceFile export(ExportCourseSectionAttendanceCommand command) {
        return exportService.exportCourseSection(command.sectionId(), command.instructorIdIfDocente(),
                command.scope(), command.format(), command.classDate());
    }
}
