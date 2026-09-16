package com.red_escuela.backend.attendance.usecase.attendance;

import com.red_escuela.backend.academicstructure.exception.SectionNotFoundException;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectSectionRepository;
import com.red_escuela.backend.attendance.dto.RosterEntryResponse;
import com.red_escuela.backend.attendance.service.AttendanceSectionAccess;
import com.red_escuela.backend.attendance.service.SectionRosterBuilder;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class GetCourseSectionAbsenceReportService implements GetCourseSectionAbsenceReportUseCase {

    private final CourseSubjectSectionRepository courseSubjectSectionRepository;
    private final AttendanceSectionAccess sectionAccess;
    private final SectionRosterBuilder rosterBuilder;

    public GetCourseSectionAbsenceReportService(CourseSubjectSectionRepository courseSubjectSectionRepository,
                                                 AttendanceSectionAccess sectionAccess,
                                                 SectionRosterBuilder rosterBuilder) {
        this.courseSubjectSectionRepository = courseSubjectSectionRepository;
        this.sectionAccess = sectionAccess;
        this.rosterBuilder = rosterBuilder;
    }

    @Override
    public List<RosterEntryResponse> get(GetCourseSectionAbsenceReportCommand command) {
        if (!courseSubjectSectionRepository.existsById(command.sectionId())) {
            throw new SectionNotFoundException(command.sectionId());
        }
        sectionAccess.requireOwnCourseSectionIfInstructorGiven(command.sectionId(), command.instructorIdIfDocente());
        return rosterBuilder.sortByAbsences(rosterBuilder.buildForCourseSection(command.sectionId()), command.minAbsences());
    }
}
