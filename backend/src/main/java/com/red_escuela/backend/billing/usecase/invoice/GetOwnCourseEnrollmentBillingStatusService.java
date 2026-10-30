package com.red_escuela.backend.billing.usecase.invoice;

import com.red_escuela.backend.billing.dto.MeEnrollmentBillingStatusResponse;
import com.red_escuela.backend.billing.service.InvoiceDetailLoader;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.exception.CourseEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.billing.exception.NotOwnEnrollmentException;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class GetOwnCourseEnrollmentBillingStatusService implements GetOwnCourseEnrollmentBillingStatusUseCase {
    private final CourseEnrollmentRepository enrollmentRepository;
    private final InvoiceDetailLoader invoiceDetailLoader;

    public GetOwnCourseEnrollmentBillingStatusService(CourseEnrollmentRepository enrollmentRepository, InvoiceDetailLoader invoiceDetailLoader) {
        this.enrollmentRepository = enrollmentRepository;
        this.invoiceDetailLoader = invoiceDetailLoader;
    }

    @Override
    @Transactional(readOnly = true)
    public MeEnrollmentBillingStatusResponse get(Long studentId, Long enrollmentId) {
        CourseEnrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new CourseEnrollmentNotFoundException(enrollmentId));
        if (!enrollment.getStudent().getId().equals(studentId)) {
            throw new NotOwnEnrollmentException(studentId, enrollmentId);
        }
        return MeEnrollmentBillingStatusResponse.of(enrollment, invoiceDetailLoader.findStandingOfCourseEnrollmentOrNull(enrollmentId), invoiceDetailLoader.byCourseEnrollment(enrollmentId));
    }
}
