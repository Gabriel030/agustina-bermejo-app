package com.red_escuela.backend.billing.service;

import java.time.Clock;
import com.red_escuela.backend.enrollment.entity.BillableEnrollment;
import com.red_escuela.backend.billing.entity.CollectionsPolicy;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.billing.entity.EnrollmentBillingStanding;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.InvoiceStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.exception.CourseEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.notification.SuspensionLiftedEvent;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
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

@Service
public class EnrollmentBillingComplianceRowProcessor {
    private final InvoiceRepository invoiceRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    public EnrollmentBillingComplianceRowProcessor(InvoiceRepository invoiceRepository, CourseEnrollmentRepository courseEnrollmentRepository,
                                                     ProgramEnrollmentRepository programEnrollmentRepository,
                                                     ApplicationEventPublisher eventPublisher, MeterRegistry meterRegistry,
                                                     Clock clock) {
        this.invoiceRepository = invoiceRepository;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.eventPublisher = eventPublisher;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processCourseEnrollment(Long id, CollectionsPolicy policy, LocalDate today) {
        CourseEnrollment enrollment = courseEnrollmentRepository.findById(id).orElseThrow(() -> new CourseEnrollmentNotFoundException(id));
        recalculateStanding(enrollment, EnrollmentSuspensionLiftedEvent.EnrollmentType.COURSE, id,
                () -> invoiceRepository.findFirstByCourseEnrollmentIdAndStatusInOrderByDueDateAsc(id, List.of(InvoiceStatus.OVERDUE)),
                () -> invoiceRepository.countByCourseEnrollmentIdAndStatus(id, InvoiceStatus.OVERDUE),
                policy, today);
        courseEnrollmentRepository.save(enrollment);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processProgramEnrollment(Long id, CollectionsPolicy policy, LocalDate today) {
        ProgramEnrollment enrollment = programEnrollmentRepository.findById(id).orElseThrow(() -> new ProgramEnrollmentNotFoundException(id));
        recalculateStanding(enrollment, EnrollmentSuspensionLiftedEvent.EnrollmentType.PROGRAM, id,
                () -> invoiceRepository.findFirstByProgramEnrollmentIdAndStatusInOrderByDueDateAsc(id, List.of(InvoiceStatus.OVERDUE)),
                () -> invoiceRepository.countByProgramEnrollmentIdAndStatus(id, InvoiceStatus.OVERDUE),
                policy, today);
        programEnrollmentRepository.save(enrollment);
    }

    private void recalculateStanding(BillableEnrollment billable, EnrollmentSuspensionLiftedEvent.EnrollmentType enrollmentType, Long enrollmentId,
                                      Supplier<Optional<Invoice>> oldestOverdueInvoice, LongSupplier overdueCount, CollectionsPolicy policy, LocalDate today) {
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
            if (previous == EnrollmentBillingStanding.SUSPENDED
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
            billable.setBillingStandingUpdatedAt(LocalDateTime.now(clock));
        }
        if (previous == EnrollmentBillingStanding.SUSPENDED && next == EnrollmentBillingStanding.UP_TO_DATE) {
            YearMonth suspendedSince = previousUpdatedAt != null ? YearMonth.from(previousUpdatedAt) : YearMonth.from(today);
            eventPublisher.publishEvent(new SuspensionLiftedEvent(billable, suspendedSince, YearMonth.from(today)));
            eventPublisher.publishEvent(EnrollmentSuspensionLiftedEvent.liftedFor(enrollmentType, enrollmentId, suspendedSince, YearMonth.from(today)));
        }
    }
}
