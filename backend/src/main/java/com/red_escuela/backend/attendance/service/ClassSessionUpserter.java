package com.red_escuela.backend.attendance.service;

import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.attendance.entity.ClassSession;
import com.red_escuela.backend.attendance.entity.ClassSessionStatus;
import com.red_escuela.backend.attendance.repository.ClassSessionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class ClassSessionUpserter {

    private final ClassSessionRepository classSessionRepository;

    public ClassSessionUpserter(ClassSessionRepository classSessionRepository) {
        this.classSessionRepository = classSessionRepository;
    }

    public void upsertHeldForProgramSection(
            SubjectSection section, LocalDate classDate, Instructor instructor, String topic, String observations) {
        ClassSession session = findOrCreateForProgramSection(section, classDate, instructor);
        applyClassData(session, topic, observations);
        classSessionRepository.save(session);
    }

    public void upsertHeldForCourseSection(
            CourseSubjectSection section, LocalDate classDate, Instructor instructor, String topic, String observations) {
        ClassSession session = findOrCreateForCourseSection(section, classDate, instructor);
        applyClassData(session, topic, observations);
        classSessionRepository.save(session);
    }

    public ClassSession findOrCreateForProgramSection(SubjectSection section, LocalDate classDate, Instructor instructor) {
        return classSessionRepository.findBySubjectSectionIdAndClassDate(section.getId(), classDate)
                .orElseGet(() -> newProgramSession(section, classDate, instructor));
    }

    public ClassSession findOrCreateForCourseSection(CourseSubjectSection section, LocalDate classDate, Instructor instructor) {
        return classSessionRepository.findByCourseSubjectSectionIdAndClassDate(section.getId(), classDate)
                .orElseGet(() -> newCourseSession(section, classDate, instructor));
    }

    private void applyClassData(ClassSession session, String topic, String observations) {
        if (session.getStatus() != ClassSessionStatus.CANCELED) {
            session.setStatus(ClassSessionStatus.HELD);
        }
        if (topic != null) {
            session.setTopic(topic);
        }
        if (observations != null) {
            session.setObservations(observations);
        }
    }

    private ClassSession newProgramSession(SubjectSection section, LocalDate classDate, Instructor instructor) {
        ClassSession session = new ClassSession();
        session.setSubjectSection(section);
        session.setClassDate(classDate);
        session.setCreatedBy(instructor);
        session.setStatus(ClassSessionStatus.SCHEDULED);
        return session;
    }

    private ClassSession newCourseSession(CourseSubjectSection section, LocalDate classDate, Instructor instructor) {
        ClassSession session = new ClassSession();
        session.setCourseSubjectSection(section);
        session.setClassDate(classDate);
        session.setCreatedBy(instructor);
        session.setStatus(ClassSessionStatus.SCHEDULED);
        return session;
    }
}
