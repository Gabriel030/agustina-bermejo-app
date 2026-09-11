package com.red_escuela.backend.attendance.usecase.attendance;

import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.exception.SectionNotFoundException;
import com.red_escuela.backend.academicstructure.repository.CourseSubjectSectionRepository;
import com.red_escuela.backend.attendance.entity.ClassSession;
import com.red_escuela.backend.attendance.entity.ClassSessionStatus;
import com.red_escuela.backend.attendance.exception.ClassSessionAlreadyCanceledException;
import com.red_escuela.backend.attendance.repository.ClassSessionRepository;
import com.red_escuela.backend.attendance.service.AttendanceSectionAccess;
import com.red_escuela.backend.attendance.service.ClassSessionUpserter;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class CancelCourseSectionClassSessionService implements CancelCourseSectionClassSessionUseCase {

    private final CourseSubjectSectionRepository courseSubjectSectionRepository;
    private final ClassSessionRepository classSessionRepository;
    private final AttendanceSectionAccess sectionAccess;
    private final ClassSessionUpserter classSessionUpserter;

    public CancelCourseSectionClassSessionService(CourseSubjectSectionRepository courseSubjectSectionRepository,
                                                   ClassSessionRepository classSessionRepository,
                                                   AttendanceSectionAccess sectionAccess,
                                                   ClassSessionUpserter classSessionUpserter) {
        this.courseSubjectSectionRepository = courseSubjectSectionRepository;
        this.classSessionRepository = classSessionRepository;
        this.sectionAccess = sectionAccess;
        this.classSessionUpserter = classSessionUpserter;
    }

    @Override
    @Transactional
    public void cancel(CancelCourseSectionClassSessionCommand command) {
        CourseSubjectSection section = courseSubjectSectionRepository.findById(command.sectionId())
                .orElseThrow(() -> new SectionNotFoundException(command.sectionId()));
        sectionAccess.requireOwnCourseSection(command.sectionId(), command.instructorId());
        Instructor instructor = sectionAccess.resolveInstructor(command.instructorId());
        ClassSession session = classSessionUpserter.findOrCreateForCourseSection(section, command.classDate(), instructor);
        if (session.getStatus() == ClassSessionStatus.CANCELED) {
            throw new ClassSessionAlreadyCanceledException(session.getId());
        }
        session.setStatus(ClassSessionStatus.CANCELED);
        classSessionRepository.save(session);
    }
}
