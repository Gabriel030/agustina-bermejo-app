package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.api.CourseOccupiedSeatsPort;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class CourseOccupiedSeatsCounter implements CourseOccupiedSeatsPort {

    private static final List<EnrollmentStatus> OCCUPYING_STATUSES =
            List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS);

    private final CourseEnrollmentRepository courseEnrollmentRepository;

    public CourseOccupiedSeatsCounter(CourseEnrollmentRepository courseEnrollmentRepository) {
        this.courseEnrollmentRepository = courseEnrollmentRepository;
    }

    @Override
    public long countOccupiedSeats(Long courseId) {
        return courseEnrollmentRepository.countByCourseIdAndStatusIn(courseId, OCCUPYING_STATUSES);
    }
}
