package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.identity.api.StudentEnrollmentActivityLookup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EnrollmentActivityLookup implements StudentEnrollmentActivityLookup {

    private static final List<ProgramEnrollmentStatus> ACTIVE_PROGRAM_STATUSES = List.of(ProgramEnrollmentStatus.ENROLLED, ProgramEnrollmentStatus.PAUSED);
    private static final List<EnrollmentStatus> ACTIVE_COURSE_STATUSES = List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.WAITLISTED);

    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;

    public EnrollmentActivityLookup(ProgramEnrollmentRepository programEnrollmentRepository, CourseEnrollmentRepository courseEnrollmentRepository) {
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasActiveEnrollments(Long studentId) {
        return programEnrollmentRepository.existsByStudentIdAndStatusIn(studentId, ACTIVE_PROGRAM_STATUSES)
                || courseEnrollmentRepository.existsByStudentIdAndStatusIn(studentId, ACTIVE_COURSE_STATUSES);
    }
}
