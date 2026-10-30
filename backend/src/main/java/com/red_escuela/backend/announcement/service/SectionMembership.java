package com.red_escuela.backend.announcement.service;

import com.red_escuela.backend.academicstructure.api.SectionTeachingFinder;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.announcement.entity.SectionType;
import com.red_escuela.backend.announcement.exception.NotSectionInstructorException;
import com.red_escuela.backend.enrollment.api.SectionEnrollmentFinder;
import com.red_escuela.backend.identity.entity.Student;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SectionMembership {

    private final SectionTeachingFinder sectionTeachingFinder;
    private final SectionEnrollmentFinder sectionEnrollmentFinder;

    public SectionMembership(SectionTeachingFinder sectionTeachingFinder,
                             SectionEnrollmentFinder sectionEnrollmentFinder) {
        this.sectionTeachingFinder = sectionTeachingFinder;
        this.sectionEnrollmentFinder = sectionEnrollmentFinder;
    }

    public void requireInstructorOf(SectionType type, Long sectionId, Long instructorId) {
        SectionKind kind = type == SectionType.PROGRAM ? SectionKind.PROGRAM : SectionKind.COURSE;
        if (!sectionTeachingFinder.teaches(kind, sectionId, instructorId)) {
            throw new NotSectionInstructorException(sectionId);
        }
    }

    public List<Student> studentsOf(SectionType type, Long sectionId) {
        if (type == SectionType.PROGRAM) {
            return sectionEnrollmentFinder
                    .findMembersOfProgramSection(sectionId)
                    .stream()
                    .map(e -> e.getStudent())
                    .toList();
        }
        return sectionEnrollmentFinder
                .findMembersOfCourseSection(sectionId)
                .stream()
                .map(e -> e.getStudent())
                .toList();
    }
}
