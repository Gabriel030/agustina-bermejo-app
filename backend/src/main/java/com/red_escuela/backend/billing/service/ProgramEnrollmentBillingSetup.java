package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.enrollment.api.ProgramAdmissionFeePort;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class ProgramEnrollmentBillingSetup implements ProgramAdmissionFeePort {
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final InvoiceRepository invoiceRepository;
    private final EntityManager entityManager;

    public ProgramEnrollmentBillingSetup(ProgramEnrollmentRepository programEnrollmentRepository, InvoiceRepository invoiceRepository, EntityManager entityManager) {
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.invoiceRepository = invoiceRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public void assignTemplateIfMissing(ProgramEnrollment enrollment) {
        if (enrollment.getCurrentBillingTemplate() == null && enrollment.getProgramCohort() != null) {
            enrollment.setCurrentBillingTemplate(entityManager.getReference(ProgramYearBillingTemplate.class, enrollment.getProgramCohort().getTemplateId()));
            programEnrollmentRepository.save(enrollment);
        }
    }

    @Override
    @Transactional
    public void attachAdmissionFee(Long programEnrollmentId, Long admissionInvoiceId) {
        ProgramEnrollment enrollment = Optional.ofNullable(entityManager.find(ProgramEnrollment.class, programEnrollmentId)).orElseThrow();
        invoiceRepository.findById(admissionInvoiceId).orElseThrow().assignInvoiceToProgramEnrollment(enrollment.getId());
        assignTemplateIfMissing(enrollment);
    }
}
