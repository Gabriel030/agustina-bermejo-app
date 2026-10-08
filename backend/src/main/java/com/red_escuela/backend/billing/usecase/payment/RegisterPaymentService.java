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
import com.red_escuela.backend.billing.events.AdmissionInvoicePaidEvent;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.entity.CreditApplication;
import com.red_escuela.backend.billing.entity.CreditNote;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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
        Map<Long, Money> appliedByAccount = new TreeMap<>();
        Map<Long, List<CreditApplication>> creditApplicationsByAccount = new TreeMap<>();
        for (int i = 0; i < ordered.size(); i++) {
            Invoice invoice = lockedInvoices.get(i);
            Money amountApplied = ordered.get(i).amountApplied();

            invoice.allocateFullInvoicePayment(NonNegativeMoney.of(amountApplied), today, new StatusChangeActor(registeredBy.getId(), null));
            PaymentAllocation allocation = new PaymentAllocation();
            allocation.setPayment(payment);
            allocation.setInvoice(invoice);
            allocation.setAmountApplied(amountApplied);
            payment.getAllocations().add(allocation);
            appliedByAccount.merge(invoice.getStudentCurrentAccountId(), amountApplied, Money::plus);
            if (request.method() == PaymentMethod.CREDIT_BALANCE) {
                creditApplicationsByAccount.computeIfAbsent(invoice.getStudentCurrentAccountId(), accountId -> new ArrayList<>())
                        .addAll(creditNoteService.consumeForInvoice(resolveOwnerStudentId(invoice), amountApplied, invoice, payment));
            }
        }

        for (Map.Entry<Long, Money> applied : appliedByAccount.entrySet()) {
            StudentCurrentAccount account = accountService.lockStudentCurrentAccount(applied.getKey());
            NonNegativeMoney appliedAmount = NonNegativeMoney.of(applied.getValue());
            if (request.method() == PaymentMethod.CREDIT_BALANCE) {
                bookConsumedLegacyCreditNotes(account, creditApplicationsByAccount.get(applied.getKey()), today);
            } else {
                accountService.recordPaymentReceivedCredit(account, payment.getId(), appliedAmount, today, "Pago " + payment.getId());
            }
            accountService.allocateUnallocatedCreditToInvoicePayment(account, appliedAmount);
        }

        lockedInvoices.forEach(this::publishAdmissionPaidIfApplicable);
        lockedInvoices.forEach(this::restoreStandingIfNoOverdueDebtLeft);
        log.info("Pago registrado: pago={}, metodo={}, monto={}, facturas={}",
                payment.getId(), request.method(), total.amount(), ordered.stream().map(PaymentAllocationRequest::invoiceId).toList());

        return payment;
    }

    @Override
    @Transactional
    public Payment registerPaymentShortOfInvoiceBalance(RegisterPaymentRequest request, Person registeredBy) {
        if (request.rejected() || request.allocations().size() != 1) {
            throw new IllegalArgumentException("a payment short of the invoice balance must carry exactly one reported allocation");
        }
        PaymentAllocationRequest reported = request.allocations().get(0);
        Money received = reported.amountApplied();
        if (received.isNegative() || received.isZero()) {
            throw new IllegalArgumentException("amountApplied debe ser mayor a cero (invoice " + reported.invoiceId() + ")");
        }
        Invoice invoice = invoiceRepository.findByIdForUpdate(reported.invoiceId())
                .orElseThrow(() -> new InvoiceNotFoundException(reported.invoiceId()));
        validateOwnershipIfStudent(invoice);
        if (!invoice.isPayable()) {
            throw new InvoiceNotPayableException(invoice.getId(), invoice.getPaymentStatus() == null ? invoice.getDocumentStatus().name() : invoice.getPaymentStatus().name());
        }
        if (received.currency() != invoice.currency()) {
            throw new CurrencyMismatchException(invoice.currency(), received.currency());
        }
        if (!invoice.getAmounts().outstandingAmount().value().isGreaterThan(received)) {
            throw new IllegalArgumentException("the received amount covers the invoice balance (invoice " + invoice.getId() + ")");
        }

        Payment payment = new Payment();
        payment.setAmount(received);
        payment.setMethod(request.method());
        payment.setStatus(PaymentStatus.CONFIRMED);
        payment.setRegisteredBy(registeredBy);
        payment.setPaidAt(LocalDateTime.now(clock));
        payment.setExternalReference(request.externalReference());
        payment.setCardInstallments(request.cardInstallments());
        payment.setNotes(request.notes());
        payment = paymentRepository.save(payment);

        LocalDate today = LocalDate.now(clock);
        StudentCurrentAccount account = accountService.lockStudentCurrentAccount(invoice.getStudentCurrentAccountId());
        accountService.recordPaymentReceivedCredit(account, payment.getId(), NonNegativeMoney.of(received), today, "Pago " + payment.getId());
        Money outstanding = invoice.getAmounts().outstandingAmount().value();
        if (!outstanding.isGreaterThan(account.getUnallocatedCreditAmount().value())) {
            invoice.allocateFullInvoicePayment(NonNegativeMoney.of(outstanding), today, new StatusChangeActor(registeredBy.getId(), null));
            PaymentAllocation allocation = new PaymentAllocation();
            allocation.setPayment(payment);
            allocation.setInvoice(invoice);
            allocation.setAmountApplied(outstanding);
            payment.getAllocations().add(allocation);
            accountService.allocateUnallocatedCreditToInvoicePayment(account, NonNegativeMoney.of(outstanding));
            publishAdmissionPaidIfApplicable(invoice);
            restoreStandingIfNoOverdueDebtLeft(invoice);
        }
        log.info("Pago por debajo del saldo acreditado en la cuenta corriente: pago={}, metodo={}, monto={}, factura={}",
                payment.getId(), request.method(), received.amount(), invoice.getId());
        return payment;
    }

    private void bookConsumedLegacyCreditNotes(StudentCurrentAccount account, List<CreditApplication> applications, LocalDate today) {
        Map<Long, List<CreditApplication>> applicationsByCreditNote = new TreeMap<>();
        applications.forEach(application -> applicationsByCreditNote
                .computeIfAbsent(application.getCreditNote().getId(), creditNoteId -> new ArrayList<>()).add(application));
        for (Map.Entry<Long, List<CreditApplication>> consumed : applicationsByCreditNote.entrySet()) {
            if (accountService.isCreditNoteBookedInLedger(consumed.getKey())) {
                continue;
            }
            CreditNote creditNote = consumed.getValue().get(0).getCreditNote();
            Money consumedNow = consumed.getValue().stream().map(CreditApplication::getAmountApplied).reduce(Money::plus).orElseThrow();
            NonNegativeMoney availableCredit = NonNegativeMoney.of(creditNote.getRemainingAmount().plus(consumedNow));
            accountService.recordCreditNoteIssuedCredit(account, creditNote.getId(), NonNegativeMoney.zero(account.getCurrency()), availableCredit,
                    today, "Nota de crédito " + creditNote.getId());
        }
    }

    private void publishAdmissionPaidIfApplicable(Invoice invoice) {
        if (invoice.getIssuingReason() == InvoiceIssuingReason.PROGRAM_ADMISSION && invoice.getPaymentStatus() == InvoicePaymentStatus.PAID) {
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
