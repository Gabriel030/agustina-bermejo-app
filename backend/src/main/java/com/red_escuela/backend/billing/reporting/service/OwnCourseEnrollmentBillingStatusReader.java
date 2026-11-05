package com.red_escuela.backend.billing.reporting.service;

import com.red_escuela.backend.billing.dto.MeEnrollmentBillingStatusResponse;
import com.red_escuela.backend.billing.service.InvoiceDetailLoader;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.exception.CourseEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.billing.exception.NotOwnEnrollmentException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OwnCourseEnrollmentBillingStatusReader {
    private final CourseEnrollmentRepository enrollmentRepository;
    private final InvoiceDetailLoader invoiceDetailLoader;

    public OwnCourseEnrollmentBillingStatusReader(CourseEnrollmentRepository enrollmentRepository, InvoiceDetailLoader invoiceDetailLoader) {
        this.enrollmentRepository = enrollmentRepository;
        this.invoiceDetailLoader = invoiceDetailLoader;
    }

    @Transactional(readOnly = true)
    public MeEnrollmentBillingStatusResponse readOwnCourseEnrollmentBillingStatus(Long studentId, Long enrollmentId) {
        CourseEnrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new CourseEnrollmentNotFoundException(enrollmentId));
        if (!enrollment.getStudent().getId().equals(studentId)) {
            throw new NotOwnEnrollmentException(studentId, enrollmentId);
        }
        return MeEnrollmentBillingStatusResponse.of(enrollment, invoiceDetailLoader.findStandingOfCourseEnrollmentOrNull(enrollmentId), invoiceDetailLoader.byCourseEnrollment(enrollmentId));
    }
}
