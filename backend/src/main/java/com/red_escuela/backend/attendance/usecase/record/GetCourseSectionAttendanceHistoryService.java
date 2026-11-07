package com.red_escuela.backend.attendance.usecase.record;

import com.red_escuela.backend.attendance.dto.AttendanceHistoryResponse;
import com.red_escuela.backend.attendance.specialist.AttendanceHistoryBuilder;
import com.red_escuela.backend.attendance.specialist.AttendanceSectionAccessChecker;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetCourseSectionAttendanceHistoryService implements GetCourseSectionAttendanceHistoryUseCase {
    private final AttendanceSectionAccessChecker sectionAccess;
    private final AttendanceHistoryBuilder historyBuilder;

    public GetCourseSectionAttendanceHistoryService(AttendanceSectionAccessChecker sectionAccess,
                                                      AttendanceHistoryBuilder historyBuilder) {
        this.sectionAccess = sectionAccess;
        this.historyBuilder = historyBuilder;
    }

    @Override
    public AttendanceHistoryResponse get(GetCourseSectionAttendanceHistoryCommand command) {
        sectionAccess.requireCourseSectionExists(command.sectionId());
        sectionAccess.requireOwnCourseSectionIfInstructorGiven(command.sectionId(), command.instructorIdIfDocente());
        return historyBuilder.buildForCourseSection(command.sectionId());
    }
}
