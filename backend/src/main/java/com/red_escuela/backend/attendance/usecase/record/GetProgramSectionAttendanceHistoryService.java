package com.red_escuela.backend.attendance.usecase.record;

import com.red_escuela.backend.attendance.dto.AttendanceHistoryResponse;
import com.red_escuela.backend.attendance.specialist.AttendanceHistoryBuilder;
import com.red_escuela.backend.attendance.specialist.AttendanceSectionAccessChecker;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetProgramSectionAttendanceHistoryService implements GetProgramSectionAttendanceHistoryUseCase {
    private final AttendanceSectionAccessChecker sectionAccess;
    private final AttendanceHistoryBuilder historyBuilder;

    public GetProgramSectionAttendanceHistoryService(AttendanceSectionAccessChecker sectionAccess,
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
