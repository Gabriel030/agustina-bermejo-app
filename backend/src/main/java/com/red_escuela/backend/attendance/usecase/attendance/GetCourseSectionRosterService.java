package com.red_escuela.backend.attendance.usecase.attendance;

import com.red_escuela.backend.academicstructure.exception.SectionNotFoundException;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectSectionRepository;
import com.red_escuela.backend.attendance.dto.RosterEntryResponse;
import com.red_escuela.backend.attendance.service.AttendanceSectionAccess;
import com.red_escuela.backend.attendance.service.SectionRosterBuilder;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class GetCourseSectionRosterService implements GetCourseSectionRosterUseCase {

    private final CourseSubjectSectionRepository courseSubjectSectionRepository;
    private final AttendanceSectionAccess sectionAccess;
    private final SectionRosterBuilder rosterBuilder;

    public GetCourseSectionRosterService(CourseSubjectSectionRepository courseSubjectSectionRepository,
                                          AttendanceSectionAccess sectionAccess,
                                          SectionRosterBuilder rosterBuilder) {
        this.courseSubjectSectionRepository = courseSubjectSectionRepository;
        this.sectionAccess = sectionAccess;
        this.rosterBuilder = rosterBuilder;
    }

    @Override
    public List<RosterEntryResponse> get(GetCourseSectionRosterCommand command) {
        if (!courseSubjectSectionRepository.existsById(command.sectionId())) {
            throw new SectionNotFoundException(command.sectionId());
        }
        sectionAccess.requireOwnCourseSection(command.sectionId(), command.instructorId());
        return rosterBuilder.buildForCourseSection(command.sectionId());
    }
}
