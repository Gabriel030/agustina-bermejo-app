package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.InvoiceAdjustmentType;
import com.red_escuela.backend.billing.entity.MonetaryAdjustmentValue;
import com.red_escuela.backend.billing.entity.CohortIntake;
import com.red_escuela.backend.billing.entity.PaymentPlanSelection;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.entity.ProgramEnrollment;
import com.red_escuela.backend.billing.exception.ProgramEnrollmentMissingCohortException;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
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
        generarMatricula(enrollment, null);
    }

    // Variante con selection (Fase 6.5): PaymentPlanSelectionService la usa para que la Invoice de
    // matrícula quede atada a la PaymentPlanSelection que acaba de calificar para una Promotion —
    // sin esto, la matrícula nunca podría llevar el descuento de "50% en la matrícula" del ejemplo
    // de la spec, porque InvoiceFactory resuelve el beneficio de promoción a partir de esa
    // referencia.
    @Transactional
    public void generarMatricula(ProgramEnrollment enrollment, PaymentPlanSelection selection) {
        ProgramCohort cohort = enrollment.getProgramCohort();
        if (cohort == null) {
            throw new ProgramEnrollmentMissingCohortException(enrollment.getId());
        }
        YearMonth period = YearMonth.from(cohort.getCursadaStart());
        // crearParaCarreraAtomica (no crearParaCarrera): la Invoice de matrícula tiene que quedar
        // atada a ESTA MISMA transacción — si aplicar el descuento de segunda cohorte de abajo
        // falla, toda la operación revierte junta, sin dejar una Invoice de matrícula huérfana
        // (sin PaymentPlanSelection y, para cohorte SECOND, sin el 50% de descuento) ya commiteada
        // por una transacción REQUIRES_NEW que ya cerró (bug real encontrado en auditoría 2026-09-04).
        Invoice invoice = invoiceFactory.crearParaCarreraAtomica(enrollment, selection, FeeType.ENROLLMENT_FEE, period, cohort.getCursadaStart());
        if (cohort.getIntake() == CohortIntake.SECOND) {
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
