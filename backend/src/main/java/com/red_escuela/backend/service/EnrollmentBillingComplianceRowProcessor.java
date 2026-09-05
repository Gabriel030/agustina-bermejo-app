package com.red_escuela.backend.service;

import com.red_escuela.backend.entity.BillableEnrollment;
import com.red_escuela.backend.entity.CollectionsPolicy;
import com.red_escuela.backend.entity.CourseEnrollment;
import com.red_escuela.backend.entity.EnrollmentBillingStanding;
import com.red_escuela.backend.entity.Invoice;
import com.red_escuela.backend.entity.InvoiceStatus;
import com.red_escuela.backend.entity.ProgramEnrollment;
import com.red_escuela.backend.exception.CourseEnrollmentNotFoundException;
import com.red_escuela.backend.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.notification.SuspensionLiftedEvent;
import com.red_escuela.backend.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.repository.InvoiceRepository;
import com.red_escuela.backend.repository.ProgramEnrollmentRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

// Bean separado de EnrollmentBillingComplianceSweepService por el mismo motivo que
// InvoiceOverdueRowProcessor: @Transactional(REQUIRES_NEW) no se respeta en una auto-invocación
// dentro de la misma clase.
@Service
public class EnrollmentBillingComplianceRowProcessor {

    private final InvoiceRepository invoiceRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final CourseEnrollmentService courseEnrollmentService;
    private final ProgramEnrollmentService programEnrollmentService;
    private final ApplicationEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;

    public EnrollmentBillingComplianceRowProcessor(InvoiceRepository invoiceRepository, CourseEnrollmentRepository courseEnrollmentRepository,
                                                     ProgramEnrollmentRepository programEnrollmentRepository,
                                                     CourseEnrollmentService courseEnrollmentService, ProgramEnrollmentService programEnrollmentService,
                                                     ApplicationEventPublisher eventPublisher, MeterRegistry meterRegistry) {
        this.invoiceRepository = invoiceRepository;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.courseEnrollmentService = courseEnrollmentService;
        this.programEnrollmentService = programEnrollmentService;
        this.eventPublisher = eventPublisher;
        this.meterRegistry = meterRegistry;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processCourseEnrollment(Long id, CollectionsPolicy policy, LocalDate today) {
        CourseEnrollment enrollment = courseEnrollmentRepository.findById(id).orElseThrow(() -> new CourseEnrollmentNotFoundException(id));
        recalculateStanding(enrollment,
                () -> invoiceRepository.findFirstByCourseEnrollmentIdAndStatusInOrderByDueDateAsc(id, List.of(InvoiceStatus.OVERDUE)),
                () -> invoiceRepository.countByCourseEnrollmentIdAndStatus(id, InvoiceStatus.OVERDUE),
                policy, today, () -> courseEnrollmentService.darDeBaja(id));
        courseEnrollmentRepository.save(enrollment);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processProgramEnrollment(Long id, CollectionsPolicy policy, LocalDate today) {
        ProgramEnrollment enrollment = programEnrollmentRepository.findById(id).orElseThrow(() -> new ProgramEnrollmentNotFoundException(id));
        recalculateStanding(enrollment,
                () -> invoiceRepository.findFirstByProgramEnrollmentIdAndStatusInOrderByDueDateAsc(id, List.of(InvoiceStatus.OVERDUE)),
                () -> invoiceRepository.countByProgramEnrollmentIdAndStatus(id, InvoiceStatus.OVERDUE),
                policy, today, () -> programEnrollmentService.darDeBaja(id));
        programEnrollmentRepository.save(enrollment);
    }

    // Criterio doble (decisión 2026-09-03): SUSPENDED/WITHDRAWN_FOR_NONPAYMENT se disparan por
    // CUALQUIERA de las dos condiciones — antigüedad en días de la Invoice OVERDUE más vieja, o
    // cantidad de Invoice OVERDUE simultáneas — la que se cumpla primero.
    //
    // Corrección 2026-09-04 (auditoría, hallazgo real): la condición de "cero deuda" que habilita
    // volver a UP_TO_DATE tiene que mirar SOLO Invoice OVERDUE, nunca PENDING — antes esta misma
    // consulta traía PENDING u OVERDUE, así que un enrollment SUSPENDED que saldaba toda su mora
    // pero tenía una Invoice PENDING futura ya generada (plan UPFRONT, o matrícula+primera cuota
    // nacidas juntas) nunca veía "oldestUnpaid vacío" y quedaba SUSPENDED para siempre — la propia
    // regla de negocio dice "hasta llegar a cero Invoice OVERDUE", no "cero PENDING u OVERDUE".
    private void recalculateStanding(BillableEnrollment billable, Supplier<Optional<Invoice>> oldestOverdueInvoice,
                                      LongSupplier overdueCount, CollectionsPolicy policy, LocalDate today, Runnable withdraw) {
        EnrollmentBillingStanding previous = billable.getBillingStanding();
        LocalDateTime previousUpdatedAt = billable.getBillingStandingUpdatedAt();
        long overdueInvoiceCount = overdueCount.getAsLong();

        EnrollmentBillingStanding next;
        if (overdueInvoiceCount == 0) {
            next = EnrollmentBillingStanding.UP_TO_DATE;
        } else {
            Invoice oldestOverdue = oldestOverdueInvoice.get()
                    .orElseThrow(() -> new IllegalStateException("countByStatus(OVERDUE) > 0 pero no se encontró ninguna Invoice OVERDUE"));
            long daysOverdue = ChronoUnit.DAYS.between(oldestOverdue.getDueDate(), today);
            if (daysOverdue > policy.getWithdrawalAfterDays() || overdueInvoiceCount >= policy.getWithdrawalAfterInvoiceCount()) {
                meterRegistry.counter("billing_withdrawal_attempt_total").increment();
                next = EnrollmentBillingStanding.WITHDRAWN_FOR_NONPAYMENT;
                withdraw.run();
                meterRegistry.counter("billing_withdrawal_success_total").increment();
                // SUSPENDED es "pegajoso" mientras siga habiendo alguna Invoice OVERDUE: un pago
                // parcial que hace bajar días/cantidad por debajo del umbral no debe destrabar la
                // suspensión solo — hace falta llegar a overdueInvoiceCount == 0 (rama de arriba)
                // para volver a UP_TO_DATE (test 134).
            } else if (previous == EnrollmentBillingStanding.SUSPENDED
                    || daysOverdue > policy.getSuspensionAfterDays() || overdueInvoiceCount >= policy.getSuspensionAfterInvoiceCount()) {
                if (previous != EnrollmentBillingStanding.SUSPENDED) {
                    meterRegistry.counter("billing_suspension_attempt_total").increment();
                    meterRegistry.counter("billing_suspension_success_total").increment();
                }
                next = EnrollmentBillingStanding.SUSPENDED;
            } else if (daysOverdue > policy.getGracePeriodDays()) {
                next = EnrollmentBillingStanding.OVERDUE;
            } else {
                next = EnrollmentBillingStanding.UP_TO_DATE;
            }
        }

        if (next != previous) {
            billable.setBillingStanding(next);
            billable.setBillingStandingUpdatedAt(LocalDateTime.now());
        }
        if (previous == EnrollmentBillingStanding.SUSPENDED && next == EnrollmentBillingStanding.UP_TO_DATE) {
            YearMonth suspendedSince = previousUpdatedAt != null ? YearMonth.from(previousUpdatedAt) : YearMonth.from(today);
            eventPublisher.publishEvent(new SuspensionLiftedEvent(billable, suspendedSince, YearMonth.from(today)));
        }
    }
}
