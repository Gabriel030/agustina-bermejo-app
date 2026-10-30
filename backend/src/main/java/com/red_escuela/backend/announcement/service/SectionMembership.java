package com.red_escuela.backend.announcement.service;

import com.red_escuela.backend.academicstructure.api.SectionTeachingFinder;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.announcement.entity.SectionType;
import com.red_escuela.backend.announcement.exception.NotSectionInstructorException;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.CourseSubjectSectionEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;
import com.red_escuela.backend.identity.entity.Student;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SectionMembership {

    public static final List<EnrollmentStatus> ENROLLED_STATUSES = List.of(
            EnrollmentStatus.ENROLLED,
            EnrollmentStatus.IN_PROGRESS,
            EnrollmentStatus.REGULAR,
            EnrollmentStatus.APPROVED,
            EnrollmentStatus.FAILED);

    private final SectionTeachingFinder sectionTeachingFinder;
    private final SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository;
    private final CourseSubjectSectionEnrollmentRepository courseSubjectSectionEnrollmentRepository;

    public SectionMembership(SectionTeachingFinder sectionTeachingFinder,
                             SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository,
                             CourseSubjectSectionEnrollmentRepository courseSubjectSectionEnrollmentRepository) {
        this.sectionTeachingFinder = sectionTeachingFinder;
        this.subjectSectionEnrollmentRepository = subjectSectionEnrollmentRepository;
        this.courseSubjectSectionEnrollmentRepository = courseSubjectSectionEnrollmentRepository;
    }

    public void requireInstructorOf(SectionType type, Long sectionId, Long instructorId) {
        SectionKind kind = type == SectionType.PROGRAM ? SectionKind.PROGRAM : SectionKind.COURSE;
        if (!sectionTeachingFinder.teaches(kind, sectionId, instructorId)) {
            throw new NotSectionInstructorException(sectionId);
        }
    }

    public List<Student> studentsOf(SectionType type, Long sectionId) {
        if (type == SectionType.PROGRAM) {
            return subjectSectionEnrollmentRepository
                    .findBySubjectSectionIdAndStatusIn(sectionId, ENROLLED_STATUSES)
                    .stream()
                    .map(e -> e.getStudent())
                    .toList();
        }
        return courseSubjectSectionEnrollmentRepository
                .findByCourseSubjectSectionIdAndStatusIn(sectionId, ENROLLED_STATUSES)
                .stream()
                .map(e -> e.getStudent())
                .toList();
    }
}
