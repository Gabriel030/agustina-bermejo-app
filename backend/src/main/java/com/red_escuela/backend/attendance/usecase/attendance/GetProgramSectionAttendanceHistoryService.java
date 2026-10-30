package com.red_escuela.backend.attendance.usecase.attendance;

import com.red_escuela.backend.attendance.dto.AttendanceHistoryResponse;
import com.red_escuela.backend.attendance.service.AttendanceHistoryBuilder;
import com.red_escuela.backend.attendance.service.AttendanceSectionAccess;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetProgramSectionAttendanceHistoryService implements GetProgramSectionAttendanceHistoryUseCase {
    private final AttendanceSectionAccess sectionAccess;
    private final AttendanceHistoryBuilder historyBuilder;

    public GetProgramSectionAttendanceHistoryService(AttendanceSectionAccess sectionAccess,
                                                       AttendanceHistoryBuilder historyBuilder) {
        this.sectionAccess = sectionAccess;
        this.historyBuilder = historyBuilder;
    }

    @Override
    public AttendanceHistoryResponse get(GetProgramSectionAttendanceHistoryCommand command) {
        sectionAccess.requireProgramSectionExists(command.sectionId());
        sectionAccess.requireOwnProgramSectionIfInstructorGiven(command.sectionId(), command.instructorIdIfDocente());
        return historyBuilder.buildForProgramSection(command.sectionId());
    }
}
