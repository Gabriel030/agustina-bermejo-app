package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.enrollment.api.ProgramAdmissionFeePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProgramEnrollmentBillingSetup implements ProgramAdmissionFeePort {
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final InvoiceRepository invoiceRepository;

    public ProgramEnrollmentBillingSetup(ProgramEnrollmentRepository programEnrollmentRepository, InvoiceRepository invoiceRepository) {
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.invoiceRepository = invoiceRepository;
    }

    @Transactional
    public void assignTemplateIfMissing(ProgramEnrollment enrollment) {
        if (enrollment.getCurrentBillingTemplate() == null && enrollment.getProgramCohort() != null) {
            enrollment.setCurrentBillingTemplate(enrollment.getProgramCohort().getTemplate());
            programEnrollmentRepository.save(enrollment);
        }
    }

    @Override
    @Transactional
    public void attachAdmissionFee(Long programEnrollmentId, Long admissionInvoiceId) {
        attachAdmissionFee(programEnrollmentRepository.findById(programEnrollmentId).orElseThrow(), admissionInvoiceId);
    }

    @Transactional
    public void attachAdmissionFee(ProgramEnrollment enrollment, Long admissionInvoiceId) {
        attachAdmissionFee(enrollment, invoiceRepository.findById(admissionInvoiceId).orElseThrow());
    }

    @Transactional
    public void attachAdmissionFee(ProgramEnrollment enrollment, Invoice admissionInvoice) {
        admissionInvoice.assignInvoiceToProgramEnrollment(enrollment.getId());
        assignTemplateIfMissing(enrollment);
    }
}
