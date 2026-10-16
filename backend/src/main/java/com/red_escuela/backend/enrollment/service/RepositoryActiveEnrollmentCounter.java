package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.api.ActiveEnrollmentCounter;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class RepositoryActiveEnrollmentCounter implements ActiveEnrollmentCounter {
    private static final List<ProgramEnrollmentStatus> ACTIVE_PROGRAM_STATUSES = List.of(ProgramEnrollmentStatus.ENROLLED, ProgramEnrollmentStatus.PAUSED);
    private static final List<EnrollmentStatus> ACTIVE_COURSE_STATUSES = List.of(EnrollmentStatus.ENROLLED);

    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;

    public RepositoryActiveEnrollmentCounter(ProgramEnrollmentRepository programEnrollmentRepository, CourseEnrollmentRepository courseEnrollmentRepository) {
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public long countActiveProgramEnrollments(Long programId) {
        return programEnrollmentRepository.countByProgramIdAndStatusIn(programId, ACTIVE_PROGRAM_STATUSES);
    }

    @Override
    @Transactional(readOnly = true)
    public long countActiveCourseEnrollments(Long courseId) {
        return courseEnrollmentRepository.countByCourseIdAndStatusIn(courseId, ACTIVE_COURSE_STATUSES);
    }
}
