package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.billing.account.service.CurrentAccountAllocationService;
import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.events.AccountCreditAllocatedEvent;
import com.red_escuela.backend.billing.events.PaymentApprovedEvent;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.event.InvoicePaidDomainEvent;
import com.red_escuela.backend.billing.invoice.service.InvoiceAllocationQueryService;
import com.red_escuela.backend.billing.operation.BillingOperation;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.billing.payment.domain.PaymentCheckoutItem;
import com.red_escuela.backend.billing.payment.domain.Payment;
import com.red_escuela.backend.billing.payment.domain.PendingBankTransferDecisionResult;
import com.red_escuela.backend.billing.payment.repository.PaymentCheckoutItemRepository;
import com.red_escuela.backend.billing.payment.repository.PaymentRepository;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.event.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ApprovePendingBankTransferPaymentOperation implements BillingOperation<ApprovePendingBankTransferPaymentCommand, PendingBankTransferDecisionResult> {
    private static final Logger log = LoggerFactory.getLogger(ApprovePendingBankTransferPaymentOperation.class);
    private static final String RELEASE_REASON = "El monto del comprobante aprobado no cubre esta factura";

    private final StudentCurrentAccountService accountService;
    private final CurrentAccountAllocationService allocationService;
    private final InvoiceAllocationQueryService invoiceQueries;
    private final PaymentRepository payments;
    private final PaymentCheckoutItemRepository checkoutItems;
    private final PendingTransferReviewRelease reviewRelease;
    private final Clock clock;

    public ApprovePendingBankTransferPaymentOperation(StudentCurrentAccountService accountService, CurrentAccountAllocationService allocationService,
                                                      InvoiceAllocationQueryService invoiceQueries, PaymentRepository payments, PaymentCheckoutItemRepository checkoutItems,
                                                      PendingTransferReviewRelease reviewRelease, Clock clock) {
        this.accountService = accountService;
        this.allocationService = allocationService;
        this.invoiceQueries = invoiceQueries;
        this.payments = payments;
        this.checkoutItems = checkoutItems;
        this.reviewRelease = reviewRelease;
        this.clock = clock;
    }

    @Override
    public BillingOperationType type() {
        return BillingOperationType.APPROVE_PENDING_BANK_TRANSFER_PAYMENT;
    }

    @Override
    public Class<ApprovePendingBankTransferPaymentCommand> contextType() {
        return ApprovePendingBankTransferPaymentCommand.class;
    }

    @Override
    public List<String> validationMoments() {
        return List.of();
    }

    @Override
    public List<Class<? extends DomainEvent>> publishedEventTypes() {
        return List.of(PaymentApprovedEvent.class, AccountCreditAllocatedEvent.class, InvoicePaidDomainEvent.class);
    }

    @Override
    public void requireValidContext(ApprovePendingBankTransferPaymentCommand command) {
    }

    @Override
    public PendingBankTransferDecisionResult processOperation(ApprovePendingBankTransferPaymentCommand command) {
        LocalDate today = LocalDate.now(clock);
        Payment payment = payments.findById(command.paymentId()).orElseThrow();
        StudentCurrentAccount account = accountService.lockStudentCurrentAccount(payment.getStudentCurrentAccountId());
        invoiceQueries.lockOpenInvoicesOfAccountInIdOrder(account.getId());
        List<PaymentCheckoutItem> items = checkoutItems.findByPaymentIdOrderByPositionAsc(payment.getId());
        Map<Long, Invoice> invoicesById = invoiceQueries.findInvoicesByIds(items.stream().map(PaymentCheckoutItem::getInvoiceId).toList()).stream()
                .collect(Collectors.toMap(Invoice::getId, Function.identity()));
        NonNegativeMoney declaredAmount = payment.getAmount();
        LocalDate uploadDate = payment.getStartedAt().toLocalDate();
        payment.approvePayment(declaredAmount, uploadDate, command.reviewer(), null);
        payments.save(payment);
        accountService.recordPaymentReceivedCredit(account, payment.getId(), declaredAmount, uploadDate, "Pago " + payment.getId());
        allocationService.allocateAccountCreditToOldestInvoices(account, today, command.reviewer());
        List<Long> released = reviewRelease.releaseProtectedItemsStillUnderReview(items, invoicesById, today, command.reviewer(), RELEASE_REASON);
        log.info("Pago pendiente por transferencia aprobado y acreditado: pago={}, cuenta={}, monto={}, fechaDeSubida={}, facturasLiberadasDeRevision={}", payment.getId(),
                account.getId(), declaredAmount.value().amount(), uploadDate, released);
        return new PendingBankTransferDecisionResult(payment.getId(), account.getId(), uploadDate, declaredAmount, released);
    }

    @Override
    public List<? extends DomainEvent> domainEventsOf(ApprovePendingBankTransferPaymentCommand command, PendingBankTransferDecisionResult result) {
        return List.of(PaymentApprovedEvent.approvedFor(result.paymentId(), result.studentCurrentAccountId(), result.effectiveDate(), result.amount()));
    }
}
