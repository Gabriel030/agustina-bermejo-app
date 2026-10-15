package com.red_escuela.backend.billing.payment.service;

import com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus;
import com.red_escuela.backend.billing.events.AdmissionInvoicePaidEvent;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentEvent;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.invoice.domain.event.InvoicePaidDomainEvent;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.invoice.service.InvoiceViewLoader;
import com.red_escuela.backend.billing.service.EnrollmentSuspensionLiftedEvent;
import com.red_escuela.backend.enrollment.entity.BillableEnrollment;
import com.red_escuela.backend.notification.SuspensionLiftedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;

@Component
public class InvoicePaidFollowUp {
    private final InvoiceRepository invoiceRepository;
    private final InvoiceViewLoader invoiceViewLoader;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public InvoicePaidFollowUp(InvoiceRepository invoiceRepository, InvoiceViewLoader invoiceViewLoader, ApplicationEventPublisher eventPublisher, Clock clock) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceViewLoader = invoiceViewLoader;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @EventListener
    public void followUpInvoicePaidByAllocation(InvoicePaidDomainEvent event) {
        if (!InvoicePaymentEvent.FULL_PAYMENT_ALLOCATED.name().equals(event.cause())) {
            return;
        }
        invoiceRepository.findById(event.invoiceId()).ifPresent(invoice -> {
            publishAdmissionPaidIfApplicable(invoice);
            restoreStandingIfNoOverdueDebtLeft(invoice);
        });
    }

    private void publishAdmissionPaidIfApplicable(Invoice invoice) {
        if (invoice.getIssuingReason() != null && invoice.getIssuingReason().isAdmission() && invoice.getPaymentStatus() == InvoicePaymentStatus.PAID) {
            eventPublisher.publishEvent(AdmissionInvoicePaidEvent.paidFor(invoice.getId()));
        }
    }

    private void restoreStandingIfNoOverdueDebtLeft(Invoice invoice) {
        invoice.findCourseEnrollmentId().flatMap(invoiceViewLoader::findCourseEnrollment).ifPresent(enrollment ->
                restoreStanding(enrollment, EnrollmentSuspensionLiftedEvent.EnrollmentType.COURSE, enrollment.getId(),
                        invoiceRepository.countByCourseEnrollmentIdAndPaymentStatus(enrollment.getId(), InvoicePaymentStatus.OVERDUE)));
        invoice.findProgramEnrollmentId().flatMap(invoiceViewLoader::findProgramEnrollment).ifPresent(enrollment ->
                restoreStanding(enrollment, EnrollmentSuspensionLiftedEvent.EnrollmentType.PROGRAM, enrollment.getId(),
                        invoiceRepository.countByProgramEnrollmentIdAndPaymentStatus(enrollment.getId(), InvoicePaymentStatus.OVERDUE)));
    }

    private void restoreStanding(BillableEnrollment enrollment, EnrollmentSuspensionLiftedEvent.EnrollmentType enrollmentType, Long enrollmentId, long overdueInvoices) {
        if (overdueInvoices > 0) {
            return;
        }
        boolean wasSuspended = enrollment.getPaymentStatus() == EnrollmentPaymentStatus.SUSPENDED;
        LocalDateTime suspendedAt = enrollment.getPaymentStatusUpdatedAt();
        if (enrollment.settleArrears() && wasSuspended) {
            YearMonth suspendedSince = suspendedAt != null ? YearMonth.from(suspendedAt) : YearMonth.now(clock);
            eventPublisher.publishEvent(new SuspensionLiftedEvent(enrollment, suspendedSince, YearMonth.now(clock)));
            eventPublisher.publishEvent(EnrollmentSuspensionLiftedEvent.liftedFor(enrollmentType, enrollmentId, suspendedSince, YearMonth.now(clock)));
        }
    }
}
