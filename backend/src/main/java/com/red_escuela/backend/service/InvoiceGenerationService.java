package com.red_escuela.backend.service;

import com.red_escuela.backend.entity.CourseEnrollment;
import com.red_escuela.backend.entity.FeeType;
import com.red_escuela.backend.entity.Invoice;
import com.red_escuela.backend.entity.PaymentPlanCadence;
import com.red_escuela.backend.entity.PaymentPlanSelection;
import com.red_escuela.backend.entity.ProgramEnrollment;
import com.red_escuela.backend.exception.NoActivePaymentPlanException;
import com.red_escuela.backend.repository.PaymentPlanSelectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;

// Invoked monthly by @Scheduled (a later phase wires the cron trigger) and on demand from
// ProgramEnrollmentBillingScheduler at enrollment time.
@Service
public class InvoiceGenerationService {

    private final PaymentPlanSelectionRepository paymentPlanSelectionRepository;
    private final InvoiceFactory invoiceFactory;
    private final RecurringFeeWindowService recurringFeeWindowService;

    public InvoiceGenerationService(PaymentPlanSelectionRepository paymentPlanSelectionRepository, InvoiceFactory invoiceFactory,
                                     RecurringFeeWindowService recurringFeeWindowService) {
        this.paymentPlanSelectionRepository = paymentPlanSelectionRepository;
        this.invoiceFactory = invoiceFactory;
        this.recurringFeeWindowService = recurringFeeWindowService;
    }

    // Fase 6.3 adds the SUSPENDED/WITHDRAWN_FOR_NONPAYMENT billingStanding check here — that
    // status doesn't exist yet in this phase.
    @Transactional
    public void generarProximaCuotaParaCurso(CourseEnrollment courseEnrollment, YearMonth today) {
        PaymentPlanSelection selection = paymentPlanSelectionRepository.findActiveForCourseEnrollment(courseEnrollment.getId(), today)
                .orElseThrow(() -> new NoActivePaymentPlanException("No active payment plan for course enrollment " + courseEnrollment.getId()));
        generarSegunCadencia(selection, courseEnrollment, null, today);
    }

    @Transactional
    public void generarProximaCuotaParaCarrera(ProgramEnrollment programEnrollment, YearMonth today) {
        PaymentPlanSelection selection = paymentPlanSelectionRepository.findActiveForProgramEnrollment(programEnrollment.getId(), today)
                .orElseThrow(() -> new NoActivePaymentPlanException("No active payment plan for program enrollment " + programEnrollment.getId()));
        generarSegunCadencia(selection, null, programEnrollment, today);
    }

    private void generarSegunCadencia(PaymentPlanSelection selection, CourseEnrollment courseEnrollment, ProgramEnrollment programEnrollment, YearMonth today) {
        if (selection.getPaymentPlanOption().getCadence() == PaymentPlanCadence.MONTHLY) {
            crearUna(courseEnrollment, programEnrollment, selection, today, today.atDay(1));
            return;
        }
        // UPFRONT: the whole block is born together, only on the block's first month.
        if (today.equals(selection.getEffectiveFrom())) {
            YearMonth end = selection.getEffectiveFrom().plusMonths(selection.getScopeMonths() - 1L);
            for (YearMonth month = selection.getEffectiveFrom(); !month.isAfter(end); month = month.plusMonths(1)) {
                crearUna(courseEnrollment, programEnrollment, selection, month, selection.getEffectiveFrom().atDay(1));
            }
        }
    }

    // Gap real (2026-09-03): antes de esto, nada acotaba una cuota RECURRING_FEE al Term/plantilla
    // vigente ni a su cupo de cuotas — solo se validaba una vez, al elegir un plan UPFRONT
    // (PaymentPlanSelectionService), nunca acá, que es donde de verdad nace la Invoice. Un cron
    // mensual futuro que siga llamando a este método con YearMonth.now() generaría cuotas fuera
    // de cursada (ej. agosto en un cuatrimestre marzo-julio) o por encima del tope de la
    // plantilla. No-op silencioso (mismo criterio que saveIdempotently) — no es un error del
    // llamador, es una matriculación que ya cerró su ventana o su cupo.
    private void crearUna(CourseEnrollment courseEnrollment, ProgramEnrollment programEnrollment, PaymentPlanSelection selection, YearMonth period, java.time.LocalDate dueDate) {
        boolean canBeBilled = courseEnrollment != null
                ? recurringFeeWindowService.canBeBilled(courseEnrollment, period)
                : recurringFeeWindowService.canBeBilled(programEnrollment, period);
        if (!canBeBilled) {
            return;
        }
        Invoice invoice = courseEnrollment != null
                ? invoiceFactory.crearParaCurso(courseEnrollment, selection, FeeType.RECURRING_FEE, period, dueDate)
                : invoiceFactory.crearParaCarrera(programEnrollment, selection, FeeType.RECURRING_FEE, period, dueDate);
        // No listener yet — InvoiceGeneratedEvent is wired up in Fase 6.8 (ARCA), and only
        // published for invoices actually created (invoice != null), never for idempotent no-ops.
    }
}
