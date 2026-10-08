package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProgramEnrollmentBillingSetup {
    private final ProgramEnrollmentRepository programEnrollmentRepository;

    public ProgramEnrollmentBillingSetup(ProgramEnrollmentRepository programEnrollmentRepository) {
        this.programEnrollmentRepository = programEnrollmentRepository;
    }

    @Transactional
    public void assignTemplateIfMissing(ProgramEnrollment enrollment) {
        if (enrollment.getCurrentBillingTemplate() == null && enrollment.getProgramCohort() != null) {
            enrollment.setCurrentBillingTemplate(enrollment.getProgramCohort().getTemplate());
            programEnrollmentRepository.save(enrollment);
        }
    }

    @Transactional
    public void attachAdmissionFee(ProgramEnrollment enrollment, Invoice admissionInvoice) {
        admissionInvoice.assignInvoiceToProgramEnrollment(enrollment.getId());
        assignTemplateIfMissing(enrollment);
    }
}
