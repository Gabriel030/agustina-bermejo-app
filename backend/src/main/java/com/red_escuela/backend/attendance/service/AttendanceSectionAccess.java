package com.red_escuela.backend.attendance.service;

import com.red_escuela.backend.academicstructure.api.HolidayFinder;
import com.red_escuela.backend.academicstructure.api.InstructorFinder;
import com.red_escuela.backend.academicstructure.api.SectionFinder;
import com.red_escuela.backend.academicstructure.api.SectionTeachingFinder;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.attendance.entity.Attendance;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class AttendanceSectionAccess {

    private final SectionTeachingFinder sectionTeachingFinder;
    private final SectionFinder sectionFinder;
    private final InstructorFinder instructorFinder;
    private final HolidayFinder holidayFinder;
    private final EntityManager entityManager;

    public AttendanceSectionAccess(SectionTeachingFinder sectionTeachingFinder,
                                    SectionFinder sectionFinder,
                                    InstructorFinder instructorFinder,
                                    HolidayFinder holidayFinder,
                                    EntityManager entityManager) {
        this.sectionTeachingFinder = sectionTeachingFinder;
        this.sectionFinder = sectionFinder;
        this.instructorFinder = instructorFinder;
        this.holidayFinder = holidayFinder;
        this.entityManager = entityManager;
    }

    public boolean isOwnProgramSection(Long sectionId, Long instructorId) {
        return sectionTeachingFinder.teaches(SectionKind.PROGRAM, sectionId, instructorId);
    }

    public boolean isOwnCourseSection(Long sectionId, Long instructorId) {
        return sectionTeachingFinder.teaches(SectionKind.COURSE, sectionId, instructorId);
    }

    public void requireOwnProgramSection(Long sectionId, Long instructorId) {
        sectionTeachingFinder.requireTeaches(SectionKind.PROGRAM, sectionId, instructorId);
    }

    public void requireOwnCourseSection(Long sectionId, Long instructorId) {
        sectionTeachingFinder.requireTeaches(SectionKind.COURSE, sectionId, instructorId);
    }

    public void requireOwnProgramSectionIfInstructorGiven(Long sectionId, Long instructorIdIfDocente) {
        if (instructorIdIfDocente != null) {
            requireOwnProgramSection(sectionId, instructorIdIfDocente);
        }
    }

    public void requireOwnCourseSectionIfInstructorGiven(Long sectionId, Long instructorIdIfDocente) {
        if (instructorIdIfDocente != null) {
            requireOwnCourseSection(sectionId, instructorIdIfDocente);
        }
    }

    public void requireProgramSectionExists(Long sectionId) {
        sectionFinder.requireExists(SectionKind.PROGRAM, sectionId);
    }

    public void requireCourseSectionExists(Long sectionId) {
        sectionFinder.requireExists(SectionKind.COURSE, sectionId);
    }

    public SubjectSection findProgramSection(Long sectionId) {
        requireProgramSectionExists(sectionId);
        return entityManager.getReference(SubjectSection.class, sectionId);
    }

    public CourseSubjectSection findCourseSection(Long sectionId) {
        requireCourseSectionExists(sectionId);
        return entityManager.getReference(CourseSubjectSection.class, sectionId);
    }

    public boolean isHoliday(LocalDate date) {
        return holidayFinder.isHoliday(date);
    }

    public Instructor resolveInstructor(Long instructorId) {
        instructorFinder.requireExists(instructorId);
        return entityManager.getReference(Instructor.class, instructorId);
    }

    public Long sectionIdOf(Attendance attendance) {
        if (attendance.getProgramSubjectSectionEnrollment() != null) {
            return attendance.getProgramSubjectSectionEnrollment().getSubjectSection().getId();
        }
        return attendance.getCourseSubjectSectionEnrollment().getCourseSubjectSection().getId();
    }
}
