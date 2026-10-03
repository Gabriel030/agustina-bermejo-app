package com.red_escuela.backend.billing.usecase.payment;

import com.red_escuela.backend.billing.dto.PaymentAllocationRequest;
import com.red_escuela.backend.billing.dto.RegisterPaymentRequest;
import com.red_escuela.backend.billing.entity.EnrollmentBillingStanding;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.InvoiceStatus;
import com.red_escuela.backend.billing.entity.Payment;
import com.red_escuela.backend.billing.entity.PaymentAllocation;
import com.red_escuela.backend.billing.entity.PaymentMethod;
import com.red_escuela.backend.billing.entity.PaymentStatus;
import com.red_escuela.backend.billing.exception.InvoiceNotFoundException;
import com.red_escuela.backend.billing.exception.InvoiceNotPayableException;
import com.red_escuela.backend.billing.exception.PaymentExceedsInvoiceBalanceException;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.billing.repository.PaymentRepository;
import com.red_escuela.backend.billing.service.CreditNoteService;
import com.red_escuela.backend.enrollment.entity.BillableEnrollment;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.service.CurrentStudentService;
import com.red_escuela.backend.notification.SuspensionLiftedEvent;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;

@UseCase
public class RegisterPaymentService implements RegisterPaymentUseCase {
    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final CurrentStudentService currentStudentService;
    private final CreditNoteService creditNoteService;
    private final ApplicationEventPublisher eventPublisher;

    public RegisterPaymentService(PaymentRepository paymentRepository, InvoiceRepository invoiceRepository,
                                   CurrentStudentService currentStudentService, CreditNoteService creditNoteService,
                                   ApplicationEventPublisher eventPublisher) {
        this.paymentRepository = paymentRepository;
        this.invoiceRepository = invoiceRepository;
        this.currentStudentService = currentStudentService;
        this.creditNoteService = creditNoteService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public Payment register(RegisterPaymentRequest request, Person registeredBy) {
        BigDecimal total = request.allocations().stream()
                .map(PaymentAllocationRequest::amountApplied)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (request.rejected()) {
            Payment rejected = new Payment();
            rejected.setAmount(total);
            rejected.setMethod(request.method());
            rejected.setStatus(PaymentStatus.REJECTED);
            rejected.setRejectionReason(request.rejectionReason());
            rejected.setRegisteredBy(registeredBy);
            rejected.setPaidAt(LocalDateTime.now());
            rejected.setCardInstallments(request.cardInstallments());
            rejected.setNotes(request.notes());
            return paymentRepository.save(rejected);
        }

        List<PaymentAllocationRequest> ordered = request.allocations().stream()
                .sorted(Comparator.comparing(PaymentAllocationRequest::invoiceId))
                .toList();

        List<Invoice> lockedInvoices = ordered.stream()
                .map(allocation -> invoiceRepository.findByIdForUpdate(allocation.invoiceId())
                        .orElseThrow(() -> new InvoiceNotFoundException(allocation.invoiceId())))
                .toList();

        for (int i = 0; i < ordered.size(); i++) {
            Invoice invoice = lockedInvoices.get(i);
            BigDecimal amountApplied = ordered.get(i).amountApplied();
            if (amountApplied.signum() <= 0) {
                throw new IllegalArgumentException("amountApplied debe ser mayor a cero (invoice " + invoice.getId() + ")");
            }
            validateOwnershipIfStudent(invoice);
            if (invoice.getStatus() == InvoiceStatus.CANCELLED || invoice.getStatus() == InvoiceStatus.WAIVED) {
                throw new InvoiceNotPayableException(invoice.getId(), invoice.getStatus().name());
            }
            if (amountApplied.compareTo(invoice.getRemainingBalance()) > 0) {
                throw new PaymentExceedsInvoiceBalanceException(invoice.getId(), amountApplied, invoice.getRemainingBalance());
            }
        }

        Payment payment = new Payment();
        payment.setAmount(total);
        payment.setMethod(request.method());
        payment.setStatus(PaymentStatus.CONFIRMED);
        payment.setRegisteredBy(registeredBy);
        payment.setPaidAt(LocalDateTime.now());
        payment.setExternalReference(request.externalReference());
        payment.setCardInstallments(request.cardInstallments());
        payment.setNotes(request.notes());
        payment = paymentRepository.save(payment);

        for (int i = 0; i < ordered.size(); i++) {
            Invoice invoice = lockedInvoices.get(i);
            BigDecimal amountApplied = ordered.get(i).amountApplied();

            PaymentAllocation allocation = invoice.registerPayment(amountApplied, payment);
            payment.getAllocations().add(allocation);
            if (request.method() == PaymentMethod.CREDIT_BALANCE) {
                creditNoteService.consumeForInvoice(resolveOwnerStudentId(invoice), amountApplied, invoice, payment);
            }
        }

        lockedInvoices.forEach(this::restoreStandingIfNoOverdueDebtLeft);

        return payment;
    }

    private void restoreStandingIfNoOverdueDebtLeft(Invoice invoice) {
        if (invoice.getCourseEnrollment() != null) {
            restoreStanding(invoice.getCourseEnrollment(),
                    invoiceRepository.countByCourseEnrollmentIdAndStatus(invoice.getCourseEnrollment().getId(), InvoiceStatus.OVERDUE));
        } else if (invoice.getProgramEnrollment() != null) {
            restoreStanding(invoice.getProgramEnrollment(),
                    invoiceRepository.countByProgramEnrollmentIdAndStatus(invoice.getProgramEnrollment().getId(), InvoiceStatus.OVERDUE));
        }
    }

    private void restoreStanding(BillableEnrollment enrollment, long overdueInvoices) {
        if (overdueInvoices > 0) {
            return;
        }
        boolean wasSuspended = enrollment.getBillingStanding() == EnrollmentBillingStanding.SUSPENDED;
        LocalDateTime suspendedAt = enrollment.getBillingStandingUpdatedAt();
        if (enrollment.settleArrears() && wasSuspended) {
            YearMonth suspendedSince = suspendedAt != null ? YearMonth.from(suspendedAt) : YearMonth.now();
            eventPublisher.publishEvent(new SuspensionLiftedEvent(enrollment, suspendedSince, YearMonth.now()));
        }
    }

    private void validateOwnershipIfStudent(Invoice invoice) {
        currentStudentService.findCurrentStudent().ifPresent(student -> {
            if (!student.getId().equals(resolveOwnerStudentId(invoice))) {
                throw new InvoiceNotFoundException(invoice.getId());
            }
        });
    }

    private Long resolveOwnerStudentId(Invoice invoice) {
        return invoice.getCourseEnrollment() != null ? invoice.getCourseEnrollment().getStudent().getId()
                : invoice.getProgramEnrollment() != null ? invoice.getProgramEnrollment().getStudent().getId()
                : null;
    }
}
