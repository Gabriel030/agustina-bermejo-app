package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.billing.account.service.CurrentAccountAllocationService;
import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.service.InvoiceAllocationQueryService;
import com.red_escuela.backend.billing.operation.BillingOperation;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.billing.payment.domain.Payment;
import com.red_escuela.backend.billing.payment.domain.PaymentCheckoutItem;
import com.red_escuela.backend.billing.payment.domain.PendingBankTransferDecisionResult;
import com.red_escuela.backend.billing.payment.repository.PaymentCheckoutItemRepository;
import com.red_escuela.backend.billing.payment.repository.PaymentRepository;
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
public class RejectPendingBankTransferPaymentOperation implements BillingOperation<RejectPendingBankTransferPaymentCommand, PendingBankTransferDecisionResult> {
    private static final Logger log = LoggerFactory.getLogger(RejectPendingBankTransferPaymentOperation.class);
    private static final String RELEASE_REASON = "Comprobante de transferencia rechazado";

    private final StudentCurrentAccountService accountService;
    private final InvoiceAllocationQueryService invoiceQueries;
    private final PaymentRepository payments;
    private final PaymentCheckoutItemRepository checkoutItems;
    private final PendingTransferReviewRelease reviewRelease;
    private final CurrentAccountAllocationService allocationService;
    private final Clock clock;

    public RejectPendingBankTransferPaymentOperation(StudentCurrentAccountService accountService, InvoiceAllocationQueryService invoiceQueries, PaymentRepository payments,
                                                     PaymentCheckoutItemRepository checkoutItems, PendingTransferReviewRelease reviewRelease, CurrentAccountAllocationService allocationService,
                                                     Clock clock) {
        this.accountService = accountService;
        this.invoiceQueries = invoiceQueries;
        this.payments = payments;
        this.checkoutItems = checkoutItems;
        this.reviewRelease = reviewRelease;
        this.allocationService = allocationService;
        this.clock = clock;
    }

    @Override
    public BillingOperationType type() {
        return BillingOperationType.REJECT_PENDING_BANK_TRANSFER_PAYMENT;
    }

    @Override
    public Class<RejectPendingBankTransferPaymentCommand> contextType() {
        return RejectPendingBankTransferPaymentCommand.class;
    }

    @Override
    public List<String> validationMoments() {
        return List.of();
    }

    @Override
    public List<Class<? extends DomainEvent>> publishedEventTypes() {
        return List.of();
    }

    @Override
    public void requireValidContext(RejectPendingBankTransferPaymentCommand command) {
    }

    @Override
    public PendingBankTransferDecisionResult processOperation(RejectPendingBankTransferPaymentCommand command) {
        LocalDate today = LocalDate.now(clock);
        Payment payment = payments.findById(command.paymentId()).orElseThrow();
        StudentCurrentAccount account = accountService.lockStudentCurrentAccount(payment.getStudentCurrentAccountId());
        invoiceQueries.lockOpenInvoicesOfAccountInIdOrder(payment.getStudentCurrentAccountId());
        List<PaymentCheckoutItem> items = checkoutItems.findByPaymentIdOrderByPositionAsc(payment.getId());
        Map<Long, Invoice> invoicesById = invoiceQueries.findInvoicesByIds(items.stream().map(PaymentCheckoutItem::getInvoiceId).toList()).stream()
                .collect(Collectors.toMap(Invoice::getId, Function.identity()));
        payment.rejectPayment(command.rejectionReason(), command.reviewer());
        payments.save(payment);
        List<Long> released = reviewRelease.releaseProtectedItemsStillUnderReview(items, invoicesById, today, command.reviewer(), RELEASE_REASON);
        allocationService.allocateAccountCreditToOldestInvoices(account, today, command.reviewer());
        log.warn("Pago pendiente por transferencia rechazado: pago={}, cuenta={}, facturasLiberadasDeRevision={}", payment.getId(), payment.getStudentCurrentAccountId(), released);
        return new PendingBankTransferDecisionResult(payment.getId(), payment.getStudentCurrentAccountId(), today, payment.getAmount(), released, List.of(),
                items.stream().map(PaymentCheckoutItem::getInvoiceId).toList());
    }

    @Override
    public List<? extends DomainEvent> domainEventsOf(RejectPendingBankTransferPaymentCommand command, PendingBankTransferDecisionResult result) {
        return List.of();
    }
}
