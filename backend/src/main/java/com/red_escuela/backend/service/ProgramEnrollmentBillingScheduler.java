package com.red_escuela.backend.service;

import com.red_escuela.backend.entity.FeeType;
import com.red_escuela.backend.entity.Invoice;
import com.red_escuela.backend.entity.InvoiceAdjustmentType;
import com.red_escuela.backend.entity.MonetaryAdjustmentValue;
import com.red_escuela.backend.entity.CohortIntake;
import com.red_escuela.backend.entity.ProgramCohort;
import com.red_escuela.backend.entity.ProgramEnrollment;
import com.red_escuela.backend.exception.ProgramEnrollmentMissingCohortException;
import com.red_escuela.backend.repository.InvoiceRepository;
import com.red_escuela.backend.repository.ProgramEnrollmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;

// Invoked once when the first PaymentPlanSelection is made for a ProgramEnrollment (not on
// the bare enrollment) — generates the ENROLLMENT_FEE invoice, applying the automatic 50%
// discount for a SECOND-cohort enrollment.
@Service
public class ProgramEnrollmentBillingScheduler {

    private final InvoiceFactory invoiceFactory;
    private final InvoiceRepository invoiceRepository;
    private final ProgramEnrollmentRepository programEnrollmentRepository;

    public ProgramEnrollmentBillingScheduler(InvoiceFactory invoiceFactory, InvoiceRepository invoiceRepository,
                                              ProgramEnrollmentRepository programEnrollmentRepository) {
        this.invoiceFactory = invoiceFactory;
        this.invoiceRepository = invoiceRepository;
        this.programEnrollmentRepository = programEnrollmentRepository;
    }

    @Transactional
    public void generarMatricula(ProgramEnrollment enrollment) {
        ProgramCohort cohort = enrollment.getProgramCohort();
        if (cohort == null) {
            throw new ProgramEnrollmentMissingCohortException(enrollment.getId());
        }
        YearMonth period = YearMonth.from(cohort.getCursadaStart());
        Invoice invoice = invoiceFactory.crearParaCarrera(enrollment, null, FeeType.ENROLLMENT_FEE, period, cohort.getCursadaStart());
        if (invoice != null && cohort.getIntake() == CohortIntake.SECOND) {
            // invoiceFactory.crearParaCarrera runs in its own (REQUIRES_NEW) transaction, so the
            // Invoice it returns is already detached from this method's own persistence context —
            // mutating it here doesn't get flushed on its own, needs an explicit save.
            invoice.applyAdjustment(InvoiceAdjustmentType.DISCOUNT, new MonetaryAdjustmentValue.PercentageAmount(new BigDecimal("0.5")),
                    "Matrícula proporcional — segunda cohorte del año", null);
            invoiceRepository.save(invoice);
        }
        // currentBillingTemplate arranca en la plantilla de la cohorte de origen — a diferencia
        // de programCohort (fijo), este campo es lo que avanza cuando el alumno progresa o repite
        // un año (ver ProgramEnrollmentService.avanzarAñoLectivo).
        if (enrollment.getCurrentBillingTemplate() == null) {
            enrollment.setCurrentBillingTemplate(cohort.getTemplate());
            programEnrollmentRepository.save(enrollment);
        }
    }
}
