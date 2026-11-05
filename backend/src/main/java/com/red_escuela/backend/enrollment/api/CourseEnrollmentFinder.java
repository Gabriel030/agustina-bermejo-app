package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.exception.CourseEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Transactional(readOnly = true)
public class CourseEnrollmentFinder {

    private final CourseEnrollmentRepository courseEnrollmentRepository;

    public CourseEnrollmentFinder(CourseEnrollmentRepository courseEnrollmentRepository) {
        this.courseEnrollmentRepository = courseEnrollmentRepository;
    }

    public CourseEnrollment requireById(Long id) {
        return courseEnrollmentRepository.findById(id).orElseThrow(() -> new CourseEnrollmentNotFoundException(id));
    }

    public EnrollmentBillingView requireBillingViewById(Long id) {
        CourseEnrollment enrollment = requireById(id);
        return new EnrollmentBillingView(enrollment.getId(), enrollment.getStudent().getId(), null, enrollment.getCourse().getId(), null,
                null, enrollment.getStatus(), enrollment.getWithdrawalEffectiveDate(), enrollment.getWithdrawalRegisteredOn());
    }

    public List<Long> findIdsByStudentId(Long studentId) {
        return courseEnrollmentRepository.findByStudentId(studentId).stream().map(CourseEnrollment::getId).toList();
    }
}
