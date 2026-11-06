package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.CourseSubjectSectionEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class SectionOccupancyCounter {

    private static final List<EnrollmentStatus> SEAT_OCCUPYING_STATUSES =
            List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS, EnrollmentStatus.REGULAR);
    private static final List<EnrollmentStatus> WAITING_STATUSES = List.of(EnrollmentStatus.WAITLISTED);

    private final SubjectSectionEnrollmentRepository programEnrollmentRepository;
    private final CourseSubjectSectionEnrollmentRepository courseEnrollmentRepository;

    public SectionOccupancyCounter(SubjectSectionEnrollmentRepository programEnrollmentRepository,
                                   CourseSubjectSectionEnrollmentRepository courseEnrollmentRepository) {
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
    }

    public long countSeatsTaken(SectionKind kind, Long sectionId) {
        return count(kind, sectionId, SEAT_OCCUPYING_STATUSES);
    }

    public long countWaitlisted(SectionKind kind, Long sectionId) {
        return count(kind, sectionId, WAITING_STATUSES);
    }

    private long count(SectionKind kind, Long sectionId, List<EnrollmentStatus> statuses) {
        return switch (kind) {
            case PROGRAM -> programEnrollmentRepository.countBySubjectSectionIdAndStatusIn(sectionId, statuses);
            case COURSE -> courseEnrollmentRepository.countByCourseSubjectSectionIdAndStatusIn(sectionId, statuses);
        };
    }
}
