package com.red_escuela.backend.enrollment.usecase;

import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.shared.UseCase;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@UseCase
public class OnlineOnlyEnrollmentLookupService implements OnlineOnlyEnrollmentLookup {
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;

    public OnlineOnlyEnrollmentLookupService(ProgramEnrollmentRepository programEnrollmentRepository, CourseEnrollmentRepository courseEnrollmentRepository) {
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
    }

    @Override
    public Set<Long> findOnlineOnlyProgramEnrollmentIds(Collection<Long> programEnrollmentIds) {
        return programEnrollmentIds.isEmpty() ? Set.of() : new HashSet<>(programEnrollmentRepository.findIdsOfOnlineOnlyEnrollments(programEnrollmentIds));
    }

    @Override
    public Set<Long> findOnlineOnlyCourseEnrollmentIds(Collection<Long> courseEnrollmentIds) {
        return courseEnrollmentIds.isEmpty() ? Set.of() : new HashSet<>(courseEnrollmentRepository.findIdsOfOnlineOnlyEnrollments(courseEnrollmentIds));
    }
}
