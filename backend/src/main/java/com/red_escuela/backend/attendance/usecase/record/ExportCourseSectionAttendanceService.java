package com.red_escuela.backend.attendance.usecase.record;

import com.red_escuela.backend.attendance.dto.ExportedAttendanceFile;
import com.red_escuela.backend.attendance.specialist.AttendanceExporter;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ExportCourseSectionAttendanceService implements ExportCourseSectionAttendanceUseCase {
    private final AttendanceExporter exportService;

    public ExportCourseSectionAttendanceService(AttendanceExporter exportService) {
        this.exportService = exportService;
    }

    @Override
    public ExportedAttendanceFile export(ExportCourseSectionAttendanceCommand command) {
        return exportService.exportCourseSection(command.sectionId(), command.instructorIdIfDocente(),
                command.scope(), command.format(), command.classDate());
    }
}
