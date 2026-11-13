package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.api.SectionEnrollmentActivityPort;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.CourseSubjectSectionEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class SectionEnrollmentActivityLookup implements SectionEnrollmentActivityPort {

    private static final List<EnrollmentStatus> LIVE_STATUSES = List.of(
            EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS, EnrollmentStatus.REGULAR, EnrollmentStatus.WAITLISTED);

    private final SubjectSectionEnrollmentRepository programRepository;
    private final CourseSubjectSectionEnrollmentRepository courseRepository;

    public SectionEnrollmentActivityLookup(SubjectSectionEnrollmentRepository programRepository,
                                           CourseSubjectSectionEnrollmentRepository courseRepository) {
        this.programRepository = programRepository;
        this.courseRepository = courseRepository;
    }

    @Override
    public boolean hasLiveEnrollmentsInProgramSection(Long sectionId) {
        return programRepository.existsBySubjectSectionIdAndStatusIn(sectionId, LIVE_STATUSES);
    }

    @Override
    public boolean hasLiveEnrollmentsInCourseSection(Long sectionId) {
        return courseRepository.existsByCourseSubjectSectionIdAndStatusIn(sectionId, LIVE_STATUSES);
    }

    @Override
    public boolean hasLiveEnrollmentsInTerm(Long termId) {
        return programRepository.existsBySubjectSectionTermIdAndStatusIn(termId, LIVE_STATUSES)
                || courseRepository.existsByCourseSubjectSectionTermIdAndStatusIn(termId, LIVE_STATUSES);
    }
}
