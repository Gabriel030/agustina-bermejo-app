package com.red_escuela.backend.attendance.usecase.record;

import com.red_escuela.backend.attendance.entity.ClassSession;
import com.red_escuela.backend.attendance.repository.ClassSessionRepository;
import com.red_escuela.backend.attendance.specialist.AttendanceSectionAccessChecker;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetCourseSectionClassSessionService implements GetCourseSectionClassSessionUseCase {

    private final ClassSessionRepository classSessionRepository;
    private final AttendanceSectionAccessChecker sectionAccess;

    public GetCourseSectionClassSessionService(ClassSessionRepository classSessionRepository,
                                                AttendanceSectionAccessChecker sectionAccess) {
        this.classSessionRepository = classSessionRepository;
        this.sectionAccess = sectionAccess;
    }

    @Override
    public ClassSession get(GetCourseSectionClassSessionCommand command) {
        sectionAccess.requireOwnCourseSection(command.sectionId(), command.instructorId());
        return classSessionRepository.findByCourseSubjectSectionIdAndClassDate(command.sectionId(), command.classDate()).orElse(null);
    }
}
