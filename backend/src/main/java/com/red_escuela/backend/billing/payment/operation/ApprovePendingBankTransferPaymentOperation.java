package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.billing.account.service.CurrentAccountAllocationService;
import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.events.AccountCreditAllocatedEvent;
import com.red_escuela.backend.billing.events.PaymentApprovedEvent;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.invoice.domain.event.InvoicePaidDomainEvent;
import com.red_escuela.backend.billing.invoice.service.InvoiceAllocationQueryService;
import com.red_escuela.backend.billing.operation.BillingOperation;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.billing.account.domain.AccountCreditAllocationResult;
import com.red_escuela.backend.billing.operation.PaymentVerification;
import com.red_escuela.backend.billing.payment.domain.BankTransferPaymentDetails;
import com.red_escuela.backend.billing.payment.domain.PaymentCheckoutItem;
import com.red_escuela.backend.billing.payment.policy.PaymentReviewRejections;
import com.red_escuela.backend.billing.payment.policy.PaymentVerificationCheck;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import com.red_escuela.backend.billing.payment.domain.Payment;
import com.red_escuela.backend.billing.payment.domain.PendingBankTransferDecisionResult;
import com.red_escuela.backend.billing.payment.repository.PaymentCheckoutItemRepository;
import com.red_escuela.backend.billing.payment.repository.PaymentRepository;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.event.DomainEvent;
import com.red_escuela.backend.billing.payment.exception.PaymentAlreadyApprovedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ApprovePendingBankTransferPaymentOperation implements BillingOperation<ApprovePendingBankTransferPaymentCommand, PendingBankTransferDecisionResult> {
    private static final Logger log = LoggerFactory.getLogger(ApprovePendingBankTransferPaymentOperation.class);
    private static final String PAYMENT_CREDIT_INDEX_NAME = "uk_current_account_entry_payment_credit";
    private static final String RELEASE_REASON = "El monto del comprobante aprobado no cubre esta factura";

    private final StudentCurrentAccountService accountService;
    private final CurrentAccountAllocationService allocationService;
    private final InvoiceAllocationQueryService invoiceQueries;
    private final PaymentRepository payments;
    private final PaymentCheckoutItemRepository checkoutItems;
    private final PendingTransferReviewRelease reviewRelease;
    private final PolicySet<PaymentVerificationCheck> verificationPolicies;
    private final Clock clock;

    public ApprovePendingBankTransferPaymentOperation(StudentCurrentAccountService accountService, CurrentAccountAllocationService allocationService,
                                                      InvoiceAllocationQueryService invoiceQueries, PaymentRepository payments, PaymentCheckoutItemRepository checkoutItems,
                                                      PendingTransferReviewRelease reviewRelease, PolicySet<PaymentVerificationCheck> paymentVerificationPolicySet, Clock clock) {
        this.accountService = accountService;
        this.allocationService = allocationService;
        this.invoiceQueries = invoiceQueries;
        this.payments = payments;
        this.checkoutItems = checkoutItems;
        this.reviewRelease = reviewRelease;
        this.verificationPolicies = paymentVerificationPolicySet;
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
        return List.of(verificationPolicies.moment());
    }

    @Override
    public List<Class<? extends DomainEvent>> publishedEventTypes() {
        return List.of(PaymentApprovedEvent.class, AccountCreditAllocatedEvent.class, InvoicePaidDomainEvent.class);
    }

    @Override
    public void requireValidContext(ApprovePendingBankTransferPaymentCommand command) {
        Payment payment = payments.findById(command.paymentId()).orElseThrow();
        verificationPolicies.evaluateRulesUntilFirstViolation(verificationCheckOf(payment, command.verification(), LocalDate.now(clock)))
                .throwIfDenied(PaymentReviewRejections::rejectionOf);
    }

    public static PaymentVerificationCheck verificationCheckOf(Payment payment, PaymentVerification verification, LocalDate today) {
        return new PaymentVerificationCheck(verification.verifiedAmount(), verification.transferDate(), verification.reviewNote(), payment.getCurrency(), today);
    }

    public static LocalDate verifiedTransferDateOf(Payment payment, PaymentVerification verification) {
        if (verification.transferDate() != null) {
            return verification.transferDate();
        }
        return ((BankTransferPaymentDetails) payment.getMethodDetails()).transferDate();
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
        PaymentVerification verification = command.verification();
        LocalDate uploadDate = payment.getStartedAt().toLocalDate();
        if (!uploadDate.equals(verifiedTransferDateOf(payment, verification))) {
            throw new IllegalStateException("The transfer date differs from the upload date: the receipt must be rejected before approving the payment");
        }
        NonNegativeMoney approvedAmount = verification.verifiedAmount() == null ? payment.getAmount() : NonNegativeMoney.of(verification.verifiedAmount());
        payment.approvePayment(approvedAmount, uploadDate, command.reviewer(), verification.reviewNote());
        payments.save(payment);
        recordPaymentCreditOnce(account, payment, approvedAmount, uploadDate);
        Set<Long> programAdmissionInvoiceIds = invoicesById.values().stream()
                .filter(invoice -> invoice.getIssuingReason() != null && invoice.getIssuingReason().isProgramAdmission()).map(Invoice::getId).collect(Collectors.toSet());
        Set<Long> underReviewInvoiceIdsOfThisPayment = items.stream().filter(PaymentCheckoutItem::isMarkedUnderReview).map(PaymentCheckoutItem::getInvoiceId)
                .filter(invoiceId -> invoicesById.get(invoiceId).getPaymentStatus() == InvoicePaymentStatus.PAYMENT_UNDER_REVIEW).collect(Collectors.toSet());
        AccountCreditAllocationResult allocation = allocationService.allocateApprovedTransferCreditSettlingProgramAdmissionInvoicesFirst(account, today, command.reviewer(),
                programAdmissionInvoiceIds, underReviewInvoiceIdsOfThisPayment);
        List<Long> released = reviewRelease.releaseProtectedItemsStillUnderReview(items, invoicesById, today, command.reviewer(), RELEASE_REASON);
        log.info("Pago pendiente por transferencia aprobado y acreditado: pago={}, cuenta={}, monto={}, fechaDeSubida={}, facturasLiberadasDeRevision={}", payment.getId(),
                account.getId(), approvedAmount.value().amount(), uploadDate, released);
        return new PendingBankTransferDecisionResult(payment.getId(), account.getId(), uploadDate, approvedAmount, released,
                allocation.settledInvoices().stream().map(Invoice::getId).toList(), items.stream().map(PaymentCheckoutItem::getInvoiceId).toList());
    }

    private void recordPaymentCreditOnce(StudentCurrentAccount account, Payment payment, NonNegativeMoney approvedAmount, LocalDate uploadDate) {
        try {
            accountService.recordPaymentReceivedCredit(account, payment.getId(), approvedAmount, uploadDate, "Pago " + payment.getId());
        } catch (DataIntegrityViolationException violation) {
            if (String.valueOf(violation.getMostSpecificCause().getMessage()).contains(PAYMENT_CREDIT_INDEX_NAME)) {
                throw PaymentAlreadyApprovedException.concurrentApproval();
            }
            throw violation;
        }
    }

    @Override
    public List<? extends DomainEvent> domainEventsOf(ApprovePendingBankTransferPaymentCommand command, PendingBankTransferDecisionResult result) {
        return List.of(PaymentApprovedEvent.approvedFor(result.paymentId(), result.studentCurrentAccountId(), result.effectiveDate(), result.amount()));
    }
}
