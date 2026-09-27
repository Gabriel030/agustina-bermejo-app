package com.red_escuela.backend.announcement.service;

import com.red_escuela.backend.academicstructure.repository.CourseSubjectSectionInstructorRepository;
import com.red_escuela.backend.academicstructure.repository.SubjectSectionInstructorRepository;
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

    private final SubjectSectionInstructorRepository subjectSectionInstructorRepository;
    private final CourseSubjectSectionInstructorRepository courseSubjectSectionInstructorRepository;
    private final SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository;
    private final CourseSubjectSectionEnrollmentRepository courseSubjectSectionEnrollmentRepository;

    public SectionMembership(SubjectSectionInstructorRepository subjectSectionInstructorRepository,
                             CourseSubjectSectionInstructorRepository courseSubjectSectionInstructorRepository,
                             SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository,
                             CourseSubjectSectionEnrollmentRepository courseSubjectSectionEnrollmentRepository) {
        this.subjectSectionInstructorRepository = subjectSectionInstructorRepository;
        this.courseSubjectSectionInstructorRepository = courseSubjectSectionInstructorRepository;
        this.subjectSectionEnrollmentRepository = subjectSectionEnrollmentRepository;
        this.courseSubjectSectionEnrollmentRepository = courseSubjectSectionEnrollmentRepository;
    }

    public void requireInstructorOf(SectionType type, Long sectionId, Long instructorId) {
        boolean teaches = type == SectionType.PROGRAM
                ? subjectSectionInstructorRepository.existsBySectionIdAndInstructorId(sectionId, instructorId)
                : courseSubjectSectionInstructorRepository.existsBySectionIdAndInstructorId(sectionId, instructorId);
        if (!teaches) {
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
