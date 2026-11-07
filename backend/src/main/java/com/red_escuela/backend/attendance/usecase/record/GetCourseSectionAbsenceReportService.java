package com.red_escuela.backend.attendance.usecase.record;

import com.red_escuela.backend.attendance.dto.RosterEntryResponse;
import com.red_escuela.backend.attendance.specialist.AttendanceSectionAccessChecker;
import com.red_escuela.backend.attendance.specialist.SectionRosterBuilder;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class GetCourseSectionAbsenceReportService implements GetCourseSectionAbsenceReportUseCase {
    private final AttendanceSectionAccessChecker sectionAccess;
    private final SectionRosterBuilder rosterBuilder;

    public GetCourseSectionAbsenceReportService(AttendanceSectionAccessChecker sectionAccess,
                                                 SectionRosterBuilder rosterBuilder) {
        this.sectionAccess = sectionAccess;
        this.rosterBuilder = rosterBuilder;
    }

    @Override
    public List<RosterEntryResponse> get(GetCourseSectionAbsenceReportCommand command) {
        sectionAccess.requireCourseSectionExists(command.sectionId());
        sectionAccess.requireOwnCourseSectionIfInstructorGiven(command.sectionId(), command.instructorIdIfDocente());
        return rosterBuilder.sortByAbsences(rosterBuilder.buildForCourseSection(command.sectionId()), command.minAbsences());
    }
}
