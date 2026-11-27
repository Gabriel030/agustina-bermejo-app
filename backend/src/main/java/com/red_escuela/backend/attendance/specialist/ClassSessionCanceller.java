package com.red_escuela.backend.attendance.specialist;

import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.ClassSession;
import com.red_escuela.backend.attendance.entity.ClassSessionStatus;
import com.red_escuela.backend.attendance.exception.ClassSessionAlreadyCanceledException;
import com.red_escuela.backend.attendance.events.AttendanceStatusChangedEvent;
import com.red_escuela.backend.attendance.repository.AttendanceRepository;
import com.red_escuela.backend.attendance.repository.ClassSessionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class ClassSessionCanceller {

    private final ClassSessionRepository classSessionRepository;
    private final AttendanceSectionAccessChecker sectionAccess;
    private final ClassSessionUpserter classSessionUpserter;
    private final AttendanceRepository attendanceRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ClassSessionCanceller(ClassSessionRepository classSessionRepository,
                                 AttendanceSectionAccessChecker sectionAccess,
                                 ClassSessionUpserter classSessionUpserter,
                                 AttendanceRepository attendanceRepository,
                                 ApplicationEventPublisher eventPublisher) {
        this.attendanceRepository = attendanceRepository;
        this.eventPublisher = eventPublisher;
        this.classSessionRepository = classSessionRepository;
        this.sectionAccess = sectionAccess;
        this.classSessionUpserter = classSessionUpserter;
    }

    public void cancelCourseSectionSession(Long sectionId, Long instructorId, LocalDate classDate) {
        CourseSubjectSection section = sectionAccess.findCourseSection(sectionId);
        sectionAccess.requireOwnCourseSection(sectionId, instructorId);
        Instructor instructor = sectionAccess.resolveInstructor(instructorId);
        ClassSession session = classSessionUpserter.findOrCreateForCourseSection(section, classDate, instructor);
        cancel(session);
        publishStatusChanges(attendanceRepository.findByCourseSubjectSectionEnrollment_CourseSubjectSection_IdAndClassDate(sectionId, classDate));
    }

    public void cancelProgramSectionSession(Long sectionId, Long instructorId, LocalDate classDate) {
        SubjectSection section = sectionAccess.findProgramSection(sectionId);
        sectionAccess.requireOwnProgramSection(sectionId, instructorId);
        Instructor instructor = sectionAccess.resolveInstructor(instructorId);
        ClassSession session = classSessionUpserter.findOrCreateForProgramSection(section, classDate, instructor);
        cancel(session);
        publishStatusChanges(attendanceRepository.findByProgramSubjectSectionEnrollment_SubjectSection_IdAndClassDate(sectionId, classDate));
    }

    private void publishStatusChanges(List<Attendance> attendances) {
        attendances.forEach(attendance -> eventPublisher.publishEvent(AttendanceStatusChangedEvent.of(attendance)));
    }

    private void cancel(ClassSession session) {
        if (session.getStatus() == ClassSessionStatus.CANCELED) {
            throw new ClassSessionAlreadyCanceledException(session.getId());
        }
        session.setStatus(ClassSessionStatus.CANCELED);
        classSessionRepository.save(session);
    }
}
