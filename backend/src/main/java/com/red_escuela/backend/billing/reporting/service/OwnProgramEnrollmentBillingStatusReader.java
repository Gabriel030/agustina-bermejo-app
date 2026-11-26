package com.red_escuela.backend.billing.reporting.service;

import com.red_escuela.backend.billing.dto.MeEnrollmentBillingStatusResponse;
import com.red_escuela.backend.billing.service.InvoiceDetailLoader;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.api.ProgramEnrollmentFinder;
import com.red_escuela.backend.billing.exception.NotOwnEnrollmentException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OwnProgramEnrollmentBillingStatusReader {
    private final ProgramEnrollmentFinder enrollmentFinder;
    private final InvoiceDetailLoader invoiceDetailLoader;

    public OwnProgramEnrollmentBillingStatusReader(ProgramEnrollmentFinder enrollmentFinder, InvoiceDetailLoader invoiceDetailLoader) {
        this.enrollmentFinder = enrollmentFinder;
        this.invoiceDetailLoader = invoiceDetailLoader;
    }

    @Transactional(readOnly = true)
    public MeEnrollmentBillingStatusResponse readOwnProgramEnrollmentBillingStatus(Long studentId, Long enrollmentId) {
        ProgramEnrollment enrollment = enrollmentFinder.requireById(enrollmentId);
        if (!enrollment.getStudent().getId().equals(studentId)) {
            throw new NotOwnEnrollmentException(studentId, enrollmentId);
        }
        return MeEnrollmentBillingStatusResponse.of(enrollment, invoiceDetailLoader.findStandingOfProgramEnrollmentOrNull(enrollmentId), invoiceDetailLoader.byProgramEnrollment(enrollmentId));
    }
}
