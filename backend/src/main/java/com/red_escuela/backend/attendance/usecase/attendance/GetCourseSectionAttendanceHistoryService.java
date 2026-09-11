package com.red_escuela.backend.attendance.usecase.attendance;

import com.red_escuela.backend.academicstructure.exception.SectionNotFoundException;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectSectionRepository;
import com.red_escuela.backend.attendance.dto.AttendanceHistoryResponse;
import com.red_escuela.backend.attendance.service.AttendanceHistoryBuilder;
import com.red_escuela.backend.attendance.service.AttendanceSectionAccess;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetCourseSectionAttendanceHistoryService implements GetCourseSectionAttendanceHistoryUseCase {

    private final CourseSubjectSectionRepository courseSubjectSectionRepository;
    private final AttendanceSectionAccess sectionAccess;
    private final AttendanceHistoryBuilder historyBuilder;

    public GetCourseSectionAttendanceHistoryService(CourseSubjectSectionRepository courseSubjectSectionRepository,
                                                      AttendanceSectionAccess sectionAccess,
                                                      AttendanceHistoryBuilder historyBuilder) {
        this.courseSubjectSectionRepository = courseSubjectSectionRepository;
        this.sectionAccess = sectionAccess;
        this.historyBuilder = historyBuilder;
    }

    @Override
    public AttendanceHistoryResponse get(GetCourseSectionAttendanceHistoryCommand command) {
        if (!courseSubjectSectionRepository.existsById(command.sectionId())) {
            throw new SectionNotFoundException(command.sectionId());
        }
        sectionAccess.requireOwnCourseSectionIfInstructorGiven(command.sectionId(), command.instructorIdIfDocente());
        return historyBuilder.buildForCourseSection(command.sectionId());
    }
}
