package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.billing.events.AdmissionInvoicePaidEvent;
import com.red_escuela.backend.billing.service.ProgramEnrollmentBillingSetup;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplicationStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentApplicationRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProgramEnrollmentAdmissionPaymentListener {
    private static final Logger log = LoggerFactory.getLogger(ProgramEnrollmentAdmissionPaymentListener.class);

    private final ProgramEnrollmentApplicationRepository applicationRepository;
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final ProgramEnrollmentService programEnrollmentService;
    private final ProgramEnrollmentBillingSetup programEnrollmentBillingSetup;

    public ProgramEnrollmentAdmissionPaymentListener(ProgramEnrollmentApplicationRepository applicationRepository,
                                                     ProgramEnrollmentRepository programEnrollmentRepository,
                                                     ProgramEnrollmentService programEnrollmentService,
                                                     ProgramEnrollmentBillingSetup programEnrollmentBillingSetup) {
        this.applicationRepository = applicationRepository;
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.programEnrollmentService = programEnrollmentService;
        this.programEnrollmentBillingSetup = programEnrollmentBillingSetup;
    }

    @ApplicationModuleListener
    public void confirmEnrollmentOfPaidAdmission(AdmissionInvoicePaidEvent event) {
        applicationRepository.findLockedByAdmissionInvoiceIdAndStatus(event.invoiceId(), ProgramEnrollmentApplicationStatus.APPROVED)
                .ifPresent(application -> enrollOnce(application, event.invoiceId()));
    }

    private void enrollOnce(ProgramEnrollmentApplication application, Long admissionInvoiceId) {
        if (programEnrollmentRepository.findIdByAdmissionApplicationId(application.getId()).isPresent()) {
            return;
        }
        List<ProgramEnrollment> activeEnrollments = programEnrollmentRepository.findByStudentIdAndProgramIdAndStatusIn(
                application.getStudent().getId(), application.getProgram().getId(),
                List.of(ProgramEnrollmentStatus.ENROLLED, ProgramEnrollmentStatus.PAUSED));
        if (!activeEnrollments.isEmpty()) {
            ProgramEnrollment existing = activeEnrollments.get(0);
            if (existing.getAdmissionApplication() == null) {
                existing.setAdmissionApplication(application);
            }
            return;
        }
        ProgramEnrollment enrollment = programEnrollmentService.enrollApplicantAfterAdmissionPayment(application);
        programEnrollmentBillingSetup.attachAdmissionFee(enrollment, admissionInvoiceId);
        log.info("Inscripción a carrera confirmada por el pago de la matrícula de ingreso: solicitud={}, inscripcion={}, factura={}",
                application.getId(), enrollment.getId(), admissionInvoiceId);
    }
}
