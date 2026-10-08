package com.red_escuela.backend.billing.usecase.payment;

import java.time.LocalDate;
import com.red_escuela.backend.billing.invoice.service.InvoiceViewLoader;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.billing.invoice.exception.PartialPaymentNotAllowedException;
import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import java.time.Clock;
import com.red_escuela.backend.billing.dto.PaymentAllocationRequest;
import com.red_escuela.backend.billing.dto.RegisterPaymentRequest;
import com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.entity.Payment;
import com.red_escuela.backend.billing.entity.PaymentAllocation;
import com.red_escuela.backend.billing.entity.PaymentMethod;
import com.red_escuela.backend.billing.entity.PaymentStatus;
import com.red_escuela.backend.billing.exception.InvoiceNotFoundException;
import com.red_escuela.backend.billing.exception.InvoiceNotPayableException;
import com.red_escuela.backend.billing.exception.PaymentExceedsInvoiceBalanceException;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.repository.PaymentRepository;
import com.red_escuela.backend.billing.service.CreditNoteService;
import com.red_escuela.backend.enrollment.entity.BillableEnrollment;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.service.CurrentStudentService;
import com.red_escuela.backend.billing.service.EnrollmentSuspensionLiftedEvent;
import com.red_escuela.backend.notification.SuspensionLiftedEvent;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.domain.CurrencyMismatchException;
import com.red_escuela.backend.shared.domain.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;

@UseCase
public class RegisterPaymentService implements RegisterPaymentUseCase {
    private static final Logger log = LoggerFactory.getLogger(RegisterPaymentService.class);
    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final StudentCurrentAccountService accountService;
    private final InvoiceViewLoader invoiceViewLoader;
    private final CurrentStudentService currentStudentService;
    private final CreditNoteService creditNoteService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public RegisterPaymentService(PaymentRepository paymentRepository, InvoiceRepository invoiceRepository,
                                   StudentCurrentAccountService accountService, InvoiceViewLoader invoiceViewLoader,
                                   CurrentStudentService currentStudentService, CreditNoteService creditNoteService,
                                   ApplicationEventPublisher eventPublisher,
                                   Clock clock) {
        this.paymentRepository = paymentRepository;
        this.invoiceRepository = invoiceRepository;
        this.accountService = accountService;
        this.invoiceViewLoader = invoiceViewLoader;
        this.currentStudentService = currentStudentService;
        this.creditNoteService = creditNoteService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Payment register(RegisterPaymentRequest request, Person registeredBy) {
        Money total = request.allocations().stream()
                .map(PaymentAllocationRequest::amountApplied)
                .reduce(Money::plus)
                .orElseThrow(() -> new IllegalArgumentException("allocations must not be empty"));

        if (request.rejected()) {
            Payment rejected = new Payment();
            rejected.setAmount(total);
            rejected.setMethod(request.method());
            rejected.setStatus(PaymentStatus.REJECTED);
            rejected.setRejectionReason(request.rejectionReason());
            rejected.setRegisteredBy(registeredBy);
            rejected.setPaidAt(LocalDateTime.now(clock));
            rejected.setCardInstallments(request.cardInstallments());
            rejected.setNotes(request.notes());
            Payment savedRejected = paymentRepository.save(rejected);
            log.warn("Pago rechazado registrado: pago={}, metodo={}, monto={}", savedRejected.getId(), request.method(), total.amount());
            return savedRejected;
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
            Money amountApplied = ordered.get(i).amountApplied();
            if (amountApplied.isNegative() || amountApplied.isZero()) {
                throw new IllegalArgumentException("amountApplied debe ser mayor a cero (invoice " + invoice.getId() + ")");
            }
            validateOwnershipIfStudent(invoice);
            if (!invoice.isPayable()) {
                throw new InvoiceNotPayableException(invoice.getId(), invoice.getPaymentStatus() == null ? invoice.getDocumentStatus().name() : invoice.getPaymentStatus().name());
            }
            if (amountApplied.currency() != invoice.currency()) {
                throw new CurrencyMismatchException(invoice.currency(), amountApplied.currency());
            }
            if (amountApplied.isGreaterThan(invoice.getAmounts().outstandingAmount().value())) {
                throw new PaymentExceedsInvoiceBalanceException(invoice.getId(), amountApplied, invoice.getAmounts().outstandingAmount().value());
            }
            if (invoice.getAmounts().outstandingAmount().value().isGreaterThan(amountApplied)) {
                throw new PartialPaymentNotAllowedException();
            }
        }

        Payment payment = new Payment();
        payment.setAmount(total);
        payment.setMethod(request.method());
        payment.setStatus(PaymentStatus.CONFIRMED);
        payment.setRegisteredBy(registeredBy);
        payment.setPaidAt(LocalDateTime.now(clock));
        payment.setExternalReference(request.externalReference());
        payment.setCardInstallments(request.cardInstallments());
        payment.setNotes(request.notes());
        payment = paymentRepository.save(payment);

        LocalDate today = LocalDate.now(clock);
        for (int i = 0; i < ordered.size(); i++) {
            Invoice invoice = lockedInvoices.get(i);
            Money amountApplied = ordered.get(i).amountApplied();

            invoice.allocateFullInvoicePayment(NonNegativeMoney.of(amountApplied), today, new StatusChangeActor(registeredBy.getId(), null));
            PaymentAllocation allocation = new PaymentAllocation();
            allocation.setPayment(payment);
            allocation.setInvoice(invoice);
            allocation.setAmountApplied(amountApplied);
            payment.getAllocations().add(allocation);
            StudentCurrentAccount account = accountService.lockStudentCurrentAccount(invoice.getStudentCurrentAccountId());
            accountService.recordPaymentReceivedCredit(account, payment.getId(), NonNegativeMoney.of(amountApplied), today,
                    "Pago factura " + invoice.getInvoiceNumber().value());
            if (request.method() == PaymentMethod.CREDIT_BALANCE) {
                creditNoteService.consumeForInvoice(resolveOwnerStudentId(invoice), amountApplied, invoice, payment);
            }
        }

        lockedInvoices.forEach(this::restoreStandingIfNoOverdueDebtLeft);
        log.info("Pago registrado: pago={}, metodo={}, monto={}, facturas={}",
                payment.getId(), request.method(), total.amount(), ordered.stream().map(PaymentAllocationRequest::invoiceId).toList());

        return payment;
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

    private void validateOwnershipIfStudent(Invoice invoice) {
        currentStudentService.findCurrentStudent().ifPresent(student -> {
            if (!student.getId().equals(resolveOwnerStudentId(invoice))) {
                throw new InvoiceNotFoundException(invoice.getId());
            }
        });
    }

    private Long resolveOwnerStudentId(Invoice invoice) {
        return invoiceRepository.findOwnerStudentIdByInvoiceId(invoice.getId()).orElse(null);
    }
}
