package com.red_escuela.backend.enrollment.usecase;

import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.shared.UseCase;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

@UseCase
public class EnrollmentLifecycleQueryService implements EnrollmentLifecycleQuery {
    private static final Set<ProgramEnrollmentStatus> ENDED_PROGRAM_STATUSES = EnumSet.of(ProgramEnrollmentStatus.COMPLETED, ProgramEnrollmentStatus.WITHDRAWN);
    private static final Set<EnrollmentStatus> ENDED_COURSE_STATUSES = EnumSet.of(EnrollmentStatus.WITHDRAWN, EnrollmentStatus.APPROVED, EnrollmentStatus.FAILED,
            EnrollmentStatus.WAITLIST_EXPIRED);

    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;

    public EnrollmentLifecycleQueryService(ProgramEnrollmentRepository programEnrollmentRepository, CourseEnrollmentRepository courseEnrollmentRepository) {
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
    }

    @Override
    public Optional<LocalDate> findProgramEnrollmentEndDate(Long programEnrollmentId) {
        return programEnrollmentRepository.findById(programEnrollmentId)
                .filter(enrollment -> ENDED_PROGRAM_STATUSES.contains(enrollment.getStatus()))
                .map(ProgramEnrollment::getCompletionDate)
                .map(LocalDateTime::toLocalDate);
    }

    @Override
    public Optional<LocalDate> findCourseEnrollmentEndDate(Long courseEnrollmentId) {
        return courseEnrollmentRepository.findById(courseEnrollmentId).flatMap(this::courseEndDateOf);
    }

    private Optional<LocalDate> courseEndDateOf(CourseEnrollment enrollment) {
        Optional<LocalDate> enrollmentEnd = Optional.of(enrollment).filter(ended -> ENDED_COURSE_STATUSES.contains(ended.getStatus()))
                .map(CourseEnrollment::getCompletionDate).map(LocalDateTime::toLocalDate);
        Optional<LocalDate> courseEnd = courseEnrollmentRepository.findCourseTermEndDateByEnrollmentId(enrollment.getId()).map(LocalDateTime::toLocalDate);
        return Stream.concat(enrollmentEnd.stream(), courseEnd.stream()).min(LocalDate::compareTo);
    }
}
