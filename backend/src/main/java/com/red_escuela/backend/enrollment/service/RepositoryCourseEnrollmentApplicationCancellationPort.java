package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.api.CourseEnrollmentApplicationCancellationPort;
import com.red_escuela.backend.enrollment.entity.CourseEnrollmentApplication;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentApplicationRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RepositoryCourseEnrollmentApplicationCancellationPort implements CourseEnrollmentApplicationCancellationPort {
    private final CourseEnrollmentApplicationRepository applicationRepository;

    public RepositoryCourseEnrollmentApplicationCancellationPort(CourseEnrollmentApplicationRepository applicationRepository) {
        this.applicationRepository = applicationRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasPendingApplicationOfInvoice(Long invoiceId) {
        return applicationRepository.existsPendingByInvoiceId(invoiceId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean cancelPendingApplicationOfInvoice(Long invoiceId) {
        boolean cancelled = false;
        for (CourseEnrollmentApplication application : applicationRepository.findLockedPendingByInvoiceId(invoiceId)) {
            cancelled |= application.cancelIfPending();
        }
        return cancelled;
    }
}
