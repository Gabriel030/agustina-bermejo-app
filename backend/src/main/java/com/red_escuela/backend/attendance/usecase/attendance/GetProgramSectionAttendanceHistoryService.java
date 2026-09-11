package com.red_escuela.backend.attendance.usecase.attendance;

import com.red_escuela.backend.academicstructure.exception.SectionNotFoundException;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionRepository;
import com.red_escuela.backend.attendance.dto.AttendanceHistoryResponse;
import com.red_escuela.backend.attendance.service.AttendanceHistoryBuilder;
import com.red_escuela.backend.attendance.service.AttendanceSectionAccess;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetProgramSectionAttendanceHistoryService implements GetProgramSectionAttendanceHistoryUseCase {

    private final SubjectSectionRepository subjectSectionRepository;
    private final AttendanceSectionAccess sectionAccess;
    private final AttendanceHistoryBuilder historyBuilder;

    public GetProgramSectionAttendanceHistoryService(SubjectSectionRepository subjectSectionRepository,
                                                       AttendanceSectionAccess sectionAccess,
                                                       AttendanceHistoryBuilder historyBuilder) {
        this.subjectSectionRepository = subjectSectionRepository;
        this.sectionAccess = sectionAccess;
        this.historyBuilder = historyBuilder;
    }

    @Override
    public AttendanceHistoryResponse get(GetProgramSectionAttendanceHistoryCommand command) {
        if (!subjectSectionRepository.existsById(command.sectionId())) {
            throw new SectionNotFoundException(command.sectionId());
        }
        sectionAccess.requireOwnProgramSectionIfInstructorGiven(command.sectionId(), command.instructorIdIfDocente());
        return historyBuilder.buildForProgramSection(command.sectionId());
    }
}
