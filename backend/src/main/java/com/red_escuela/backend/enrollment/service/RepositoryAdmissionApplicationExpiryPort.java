package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.api.AdmissionApplicationExpiryPort;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentApplicationRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
public class RepositoryAdmissionApplicationExpiryPort implements AdmissionApplicationExpiryPort {
    private final ProgramEnrollmentApplicationRepository applicationRepository;

    public RepositoryAdmissionApplicationExpiryPort(ProgramEnrollmentApplicationRepository applicationRepository) {
        this.applicationRepository = applicationRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> findApprovedApplicationIdsWithAdmissionClosedBefore(LocalDate today) {
        return applicationRepository.findIdsOfApprovedWithAdmissionClosedBefore(today);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> findAdmissionInvoiceIdOfApprovedApplicationWithAdmissionClosedBefore(Long applicationId, LocalDate today) {
        return applicationRepository.findAdmissionInvoiceIdOfApprovedWithAdmissionClosedBefore(applicationId, today);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean expireIfApproved(Long applicationId) {
        return applicationRepository.findLockedById(applicationId).map(application -> application.expireIfApproved()).orElse(false);
    }
}
