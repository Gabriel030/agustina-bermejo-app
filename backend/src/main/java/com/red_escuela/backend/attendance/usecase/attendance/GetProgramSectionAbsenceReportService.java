package com.red_escuela.backend.attendance.usecase.attendance;

import com.red_escuela.backend.attendance.dto.RosterEntryResponse;
import com.red_escuela.backend.attendance.service.AttendanceSectionAccess;
import com.red_escuela.backend.attendance.service.SectionRosterBuilder;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class GetProgramSectionAbsenceReportService implements GetProgramSectionAbsenceReportUseCase {
    private final AttendanceSectionAccess sectionAccess;
    private final SectionRosterBuilder rosterBuilder;

    public GetProgramSectionAbsenceReportService(AttendanceSectionAccess sectionAccess,
                                                  SectionRosterBuilder rosterBuilder) {
        this.sectionAccess = sectionAccess;
        this.rosterBuilder = rosterBuilder;
    }

    @Override
    public List<RosterEntryResponse> get(GetProgramSectionAbsenceReportCommand command) {
        sectionAccess.requireProgramSectionExists(command.sectionId());
        sectionAccess.requireOwnProgramSectionIfInstructorGiven(command.sectionId(), command.instructorIdIfDocente());
        return rosterBuilder.sortByAbsences(rosterBuilder.buildForProgramSection(command.sectionId()), command.minAbsences());
    }
}
