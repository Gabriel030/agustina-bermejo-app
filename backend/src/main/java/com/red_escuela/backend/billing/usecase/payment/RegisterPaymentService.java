package com.red_escuela.backend.billing.usecase.payment;

import java.time.LocalDate;
import com.red_escuela.backend.billing.invoice.service.InvoiceViewLoader;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.billing.invoice.exception.PartialPaymentNotAllowedException;
import com.red_escuela.backend.billing.account.domain.AccountCreditAllocationResult;
import com.red_escuela.backend.billing.account.service.CurrentAccountAllocationService;
import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.events.PaymentLeftAsAccountCreditEvent;
import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import java.time.Clock;
import com.red_escuela.backend.billing.dto.PaymentAllocationRequest;
import com.red_escuela.backend.billing.dto.RegisterPaymentRequest;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.entity.Payment;
import com.red_escuela.backend.billing.entity.PaymentAllocation;
import com.red_escuela.backend.billing.entity.PaymentMethod;
import com.red_escuela.backend.billing.entity.PaymentStatus;
import com.red_escuela.backend.billing.exception.InvoiceNotFoundException;
import com.red_escuela.backend.billing.exception.InvoiceNotPayableException;
import com.red_escuela.backend.billing.exception.PaymentExceedsInvoiceBalanceException;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.repository.PaymentRepository;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.service.CurrentStudentService;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.domain.CurrencyMismatchException;
import com.red_escuela.backend.shared.domain.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

@UseCase
public class RegisterPaymentService implements RegisterPaymentUseCase {
    private static final Logger log = LoggerFactory.getLogger(RegisterPaymentService.class);
    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final StudentCurrentAccountService accountService;
    private final CurrentAccountAllocationService allocationService;
    private final com.red_escuela.backend.billing.account.repository.PaymentAllocationRepository allocationRepository;
    private final InvoiceViewLoader invoiceViewLoader;
    private final CurrentStudentService currentStudentService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public RegisterPaymentService(PaymentRepository paymentRepository, InvoiceRepository invoiceRepository,
                                   StudentCurrentAccountService accountService, CurrentAccountAllocationService allocationService,
                                   com.red_escuela.backend.billing.account.repository.PaymentAllocationRepository allocationRepository,
                                   InvoiceViewLoader invoiceViewLoader,
                                   CurrentStudentService currentStudentService,
                                   ApplicationEventPublisher eventPublisher,
                                   Clock clock) {
        this.paymentRepository = paymentRepository;
        this.invoiceRepository = invoiceRepository;
        this.accountService = accountService;
        this.allocationService = allocationService;
        this.allocationRepository = allocationRepository;
        this.invoiceViewLoader = invoiceViewLoader;
        this.currentStudentService = currentStudentService;
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

        lockAccountsOfInvoicesInAscendingOrder(ordered);

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
            validateOwnershipIfStudent(invoice.getId());
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
        StatusChangeActor actor = new StatusChangeActor(registeredBy.getId(), null);
        creditAccountsAndAllocate(payment, ordered, lockedInvoices, today, actor);
        log.info("Pago registrado: pago={}, metodo={}, monto={}, facturas={}",
                payment.getId(), request.method(), total.amount(), ordered.stream().map(PaymentAllocationRequest::invoiceId).toList());

        return payment;
    }

    @Override
    @Transactional
    public Payment creditPaymentToAccountAndSettleInvoiceIfCovered(RegisterPaymentRequest request, Person registeredBy) {
        if (request.rejected() || request.allocations().size() != 1) {
            throw new IllegalArgumentException("a gateway payment must carry exactly one reported allocation");
        }
        PaymentAllocationRequest reported = request.allocations().get(0);
        Money received = reported.amountApplied();
        if (received.isNegative() || received.isZero()) {
            throw new IllegalArgumentException("amountApplied debe ser mayor a cero (invoice " + reported.invoiceId() + ")");
        }
        Long reportedInvoiceId = reported.invoiceId();
        Long accountId = invoiceRepository.findStudentCurrentAccountIdByInvoiceId(reportedInvoiceId)
                .orElseThrow(() -> new InvoiceNotFoundException(reportedInvoiceId));
        validateOwnershipIfStudent(reportedInvoiceId);

        LocalDate today = LocalDate.now(clock);
        StudentCurrentAccount account = accountService.lockStudentCurrentAccount(accountId);
        if (received.currency() != account.getCurrency()) {
            throw new CurrencyMismatchException(account.getCurrency(), received.currency());
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

        accountService.recordPaymentReceivedCredit(account, payment.getId(), NonNegativeMoney.of(received), today, "Pago " + payment.getId());
        Invoice reportedInvoice = invoiceRepository.findById(reportedInvoiceId).orElseThrow(() -> new InvoiceNotFoundException(reportedInvoiceId));
        AccountCreditAllocationResult allocated = allocationService.allocateAccountCreditSettlingProgramAdmissionInvoicesFirst(account, today,
                new StatusChangeActor(registeredBy.getId(), null), programAdmissionInvoiceIdsAmong(List.of(reportedInvoice)));
        linkSettledInvoicesToPayment(payment, allocated, received);
        Money unallocatedAfterwards = account.getUnallocatedCreditAmount().value();
        Money leftAsCredit = unallocatedAfterwards.isGreaterThan(received) ? received : unallocatedAfterwards;
        if (leftAsCredit.isGreaterThan(Money.zero(received.currency()))) {
            eventPublisher.publishEvent(PaymentLeftAsAccountCreditEvent.leftFor(payment.getId(), reportedInvoiceId, registeredBy.getId(), leftAsCredit.amount(),
                    received.currency().name()));
        }
        log.info("Pago acreditado en la cuenta corriente: pago={}, metodo={}, monto={}, factura={}, facturas saldadas={}",
                payment.getId(), request.method(), received.amount(), reportedInvoiceId, allocated.settledInvoices().stream().map(Invoice::getId).toList());
        return payment;
    }

    private void lockAccountsOfInvoicesInAscendingOrder(List<PaymentAllocationRequest> allocations) {
        allocations.stream()
                .map(allocation -> invoiceRepository.findStudentCurrentAccountIdByInvoiceId(allocation.invoiceId())
                        .orElseThrow(() -> new InvoiceNotFoundException(allocation.invoiceId())))
                .distinct()
                .sorted()
                .forEach(accountService::lockStudentCurrentAccount);
    }

    private void creditAccountsAndAllocate(Payment payment, List<PaymentAllocationRequest> ordered, List<Invoice> lockedInvoices, LocalDate today,
                                           StatusChangeActor actor) {
        Map<Long, Money> receivedByAccount = new TreeMap<>();
        for (int i = 0; i < ordered.size(); i++) {
            receivedByAccount.merge(lockedInvoices.get(i).getStudentCurrentAccountId(), ordered.get(i).amountApplied(), Money::plus);
        }
        for (Map.Entry<Long, Money> received : receivedByAccount.entrySet()) {
            StudentCurrentAccount account = accountService.lockStudentCurrentAccount(received.getKey());
            accountService.recordPaymentReceivedCredit(account, payment.getId(), NonNegativeMoney.of(received.getValue()), today, "Pago " + payment.getId());
            Set<Long> programAdmissionInvoiceIds = programAdmissionInvoiceIdsAmong(lockedInvoices.stream()
                    .filter(invoice -> invoice.getStudentCurrentAccountId().equals(account.getId())).toList());
            linkSettledInvoicesToPayment(payment, allocationService.allocateAccountCreditSettlingProgramAdmissionInvoicesFirst(account, today, actor, programAdmissionInvoiceIds),
                    received.getValue());
        }
    }

    private Set<Long> programAdmissionInvoiceIdsAmong(List<Invoice> payableInvoices) {
        return payableInvoices.stream().filter(invoice -> invoice.getIssuingReason() != null && invoice.getIssuingReason().isProgramAdmission())
                .map(Invoice::getId).collect(Collectors.toSet());
    }

    private void linkSettledInvoicesToPayment(Payment payment, AccountCreditAllocationResult result, Money contributedByThePayment) {
        Money remaining = contributedByThePayment;
        for (int i = 0; i < result.allocations().size() && remaining.isGreaterThan(Money.zero(remaining.currency())); i++) {
            Money allocated = result.allocations().get(i).getAmount().value();
            Money linked = allocated.isGreaterThan(remaining) ? remaining : allocated;
            PaymentAllocation link = new PaymentAllocation();
            link.setPayment(payment);
            link.setInvoice(result.settledInvoices().get(i));
            link.setAmountApplied(linked);
            payment.getAllocations().add(link);
            remaining = remaining.minus(linked);
        }
    }

    private void validateOwnershipIfStudent(Long invoiceId) {
        currentStudentService.findCurrentStudent().ifPresent(student -> {
            if (!student.getId().equals(invoiceRepository.findOwnerStudentIdByInvoiceId(invoiceId).orElse(null))) {
                throw new InvoiceNotFoundException(invoiceId);
            }
        });
    }

    private Long resolveOwnerStudentId(Invoice invoice) {
        return invoiceRepository.findOwnerStudentIdByInvoiceId(invoice.getId()).orElse(null);
    }
}
