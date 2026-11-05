package com.red_escuela.backend.attendance.service;

import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.attendance.entity.ClassSession;
import com.red_escuela.backend.attendance.entity.ClassSessionStatus;
import com.red_escuela.backend.attendance.exception.ClassSessionAlreadyCanceledException;
import com.red_escuela.backend.attendance.repository.ClassSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class ClassSessionCanceller {

    private final ClassSessionRepository classSessionRepository;
    private final AttendanceSectionAccess sectionAccess;
    private final ClassSessionUpserter classSessionUpserter;

    public ClassSessionCanceller(ClassSessionRepository classSessionRepository,
                                 AttendanceSectionAccess sectionAccess,
                                 ClassSessionUpserter classSessionUpserter) {
        this.classSessionRepository = classSessionRepository;
        this.sectionAccess = sectionAccess;
        this.classSessionUpserter = classSessionUpserter;
    }

    @Transactional
    public void cancelCourseSectionSession(Long sectionId, Long instructorId, LocalDate classDate) {
        CourseSubjectSection section = sectionAccess.findCourseSection(sectionId);
        sectionAccess.requireOwnCourseSection(sectionId, instructorId);
        Instructor instructor = sectionAccess.resolveInstructor(instructorId);
        ClassSession session = classSessionUpserter.findOrCreateForCourseSection(section, classDate, instructor);
        cancel(session);
    }

    @Transactional
    public void cancelProgramSectionSession(Long sectionId, Long instructorId, LocalDate classDate) {
        SubjectSection section = sectionAccess.findProgramSection(sectionId);
        sectionAccess.requireOwnProgramSection(sectionId, instructorId);
        Instructor instructor = sectionAccess.resolveInstructor(instructorId);
        ClassSession session = classSessionUpserter.findOrCreateForProgramSection(section, classDate, instructor);
        cancel(session);
    }

    private void cancel(ClassSession session) {
        if (session.getStatus() == ClassSessionStatus.CANCELED) {
            throw new ClassSessionAlreadyCanceledException(session.getId());
        }
        session.setStatus(ClassSessionStatus.CANCELED);
        classSessionRepository.save(session);
    }
}
