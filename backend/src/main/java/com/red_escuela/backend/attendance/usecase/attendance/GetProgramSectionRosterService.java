package com.red_escuela.backend.attendance.usecase.attendance;

import com.red_escuela.backend.academicstructure.exception.SectionNotFoundException;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionRepository;
import com.red_escuela.backend.attendance.dto.RosterEntryResponse;
import com.red_escuela.backend.attendance.service.AttendanceSectionAccess;
import com.red_escuela.backend.attendance.service.SectionRosterBuilder;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class GetProgramSectionRosterService implements GetProgramSectionRosterUseCase {

    private final SubjectSectionRepository subjectSectionRepository;
    private final AttendanceSectionAccess sectionAccess;
    private final SectionRosterBuilder rosterBuilder;

    public GetProgramSectionRosterService(SubjectSectionRepository subjectSectionRepository,
                                           AttendanceSectionAccess sectionAccess,
                                           SectionRosterBuilder rosterBuilder) {
        this.subjectSectionRepository = subjectSectionRepository;
        this.sectionAccess = sectionAccess;
        this.rosterBuilder = rosterBuilder;
    }

    @Override
    public List<RosterEntryResponse> get(GetProgramSectionRosterCommand command) {
        if (!subjectSectionRepository.existsById(command.sectionId())) {
            throw new SectionNotFoundException(command.sectionId());
        }
        sectionAccess.requireOwnProgramSection(command.sectionId(), command.instructorId());
        return rosterBuilder.buildForProgramSection(command.sectionId());
    }
}
