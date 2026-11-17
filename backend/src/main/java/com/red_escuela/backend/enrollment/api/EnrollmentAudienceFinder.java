package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@Transactional(readOnly = true)
public class EnrollmentAudienceFinder {

    private static final List<EnrollmentStatus> IN_COURSE = List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS);

    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final SubjectSectionEnrollmentRepository sectionEnrollmentRepository;

    public EnrollmentAudienceFinder(ProgramEnrollmentRepository programEnrollmentRepository,
                                    SubjectSectionEnrollmentRepository sectionEnrollmentRepository) {
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.sectionEnrollmentRepository = sectionEnrollmentRepository;
    }

    public Set<Long> personIdsInProgram(Long programId) {
        return new HashSet<>(programEnrollmentRepository.findStudentPersonIdsByProgramAndStatus(programId, ProgramEnrollmentStatus.ENROLLED));
    }

    public Set<Long> personIdsInSubject(Long subjectId) {
        return new HashSet<>(sectionEnrollmentRepository.findStudentPersonIdsBySubjectAndStatusIn(subjectId, IN_COURSE));
    }

    public Set<Long> personIdsInCourse() {
        return new HashSet<>(sectionEnrollmentRepository.findStudentPersonIdsByStatusIn(IN_COURSE));
    }

    public Set<Long> personIdsWithRegularStatus() {
        return new HashSet<>(sectionEnrollmentRepository.findStudentPersonIdsByStatusIn(List.of(EnrollmentStatus.REGULAR)));
    }

    public Set<Long> personIdsGraduated() {
        return new HashSet<>(programEnrollmentRepository.findStudentPersonIdsByStatus(ProgramEnrollmentStatus.COMPLETED));
    }

    public Set<Long> personIdsWithdrawn() {
        return new HashSet<>(programEnrollmentRepository.findStudentPersonIdsByStatus(ProgramEnrollmentStatus.WITHDRAWN));
    }
}
