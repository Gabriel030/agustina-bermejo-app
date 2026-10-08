package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.billing.events.AdmissionInvoicePaidEvent;
import com.red_escuela.backend.billing.service.ProgramEnrollmentBillingSetup;
import com.red_escuela.backend.enrollment.dto.CreateProgramEnrollmentRequest;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
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
        applicationRepository.findByAdmissionInvoiceIdAndStatus(event.invoiceId(), ProgramEnrollmentApplicationStatus.APPROVED).ifPresent(application -> {
            Long studentId = application.getStudent().getId();
            boolean alreadyEnrolled = !programEnrollmentRepository.findByStudentIdAndProgramIdAndStatusIn(
                    studentId, application.getProgram().getId(), List.of(ProgramEnrollmentStatus.ENROLLED, ProgramEnrollmentStatus.PAUSED)).isEmpty();
            if (alreadyEnrolled) {
                return;
            }
            Long cohortId = application.getProgramCohort() == null ? null : application.getProgramCohort().getId();
            ProgramEnrollment enrollment = programEnrollmentService.enrollAfterAdmissionPayment(studentId,
                    new CreateProgramEnrollmentRequest(application.getProgram().getId(), cohortId));
            programEnrollmentBillingSetup.attachAdmissionFee(enrollment, event.invoiceId());
            log.info("Inscripción a carrera confirmada por el pago de la matrícula de ingreso: solicitud={}, inscripcion={}, factura={}",
                    application.getId(), enrollment.getId(), event.invoiceId());
        });
    }
}
