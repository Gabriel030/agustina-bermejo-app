package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.billing.account.service.CurrentAccountAllocationService;
import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.annualupfront.exception.AnnualUpfrontRequestNotFoundException;
import com.red_escuela.backend.billing.annualupfront.service.AnnualUpfrontRequestPaymentFacts;
import com.red_escuela.backend.billing.annualupfront.service.AnnualUpfrontRequestPaymentTracker;
import com.red_escuela.backend.billing.events.AccountCreditAllocatedEvent;
import com.red_escuela.backend.billing.events.PaymentApprovedEvent;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentStatus;
import com.red_escuela.backend.billing.invoice.domain.event.InvoicePaidDomainEvent;
import com.red_escuela.backend.billing.invoice.service.InvoiceAllocationQueryService;
import com.red_escuela.backend.billing.operation.BillingOperation;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.billing.payment.domain.AnnualUpfrontCashPaymentResult;
import com.red_escuela.backend.billing.payment.domain.CashPaymentDetails;
import com.red_escuela.backend.billing.payment.domain.Payment;
import com.red_escuela.backend.billing.payment.domain.ReceiptInvoiceFact;
import com.red_escuela.backend.billing.payment.policy.AnnualUpfrontReceiptCheck;
import com.red_escuela.backend.billing.payment.policy.BankTransferReceiptRejections;
import com.red_escuela.backend.billing.payment.repository.PaymentRepository;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import com.red_escuela.backend.shared.event.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Component
public class RegisterAnnualUpfrontCashPaymentOperation implements BillingOperation<RegisterAnnualUpfrontCashPaymentCommand, AnnualUpfrontCashPaymentResult> {
    private static final Logger log = LoggerFactory.getLogger(RegisterAnnualUpfrontCashPaymentOperation.class);

    private final StudentCurrentAccountService accountService;
    private final CurrentAccountAllocationService allocationService;
    private final InvoiceAllocationQueryService invoiceQueries;
    private final PaymentRepository payments;
    private final PolicySet<AnnualUpfrontReceiptCheck> annualUpfrontPolicies;
    private final AnnualUpfrontRequestPaymentTracker annualUpfrontRequests;
    private final Clock clock;

    public RegisterAnnualUpfrontCashPaymentOperation(StudentCurrentAccountService accountService, CurrentAccountAllocationService allocationService,
                                                     InvoiceAllocationQueryService invoiceQueries, PaymentRepository payments,
                                                     PolicySet<AnnualUpfrontReceiptCheck> annualUpfrontApprovalPolicySet, AnnualUpfrontRequestPaymentTracker annualUpfrontRequests,
                                                     Clock clock) {
        this.accountService = accountService;
        this.allocationService = allocationService;
        this.invoiceQueries = invoiceQueries;
        this.payments = payments;
        this.annualUpfrontPolicies = annualUpfrontApprovalPolicySet;
        this.annualUpfrontRequests = annualUpfrontRequests;
        this.clock = clock;
    }

    @Override
    public BillingOperationType type() {
        return BillingOperationType.REGISTER_ANNUAL_UPFRONT_CASH_PAYMENT;
    }

    @Override
    public Class<RegisterAnnualUpfrontCashPaymentCommand> contextType() {
        return RegisterAnnualUpfrontCashPaymentCommand.class;
    }

    @Override
    public List<String> validationMoments() {
        return List.of(annualUpfrontPolicies.moment());
    }

    @Override
    public List<Class<? extends DomainEvent>> publishedEventTypes() {
        return List.of(PaymentApprovedEvent.class, AccountCreditAllocatedEvent.class, InvoicePaidDomainEvent.class);
    }

    @Override
    public void requireValidContext(RegisterAnnualUpfrontCashPaymentCommand command) {
        annualUpfrontRequests.findInvoiceIdOfRequest(command.requestId()).orElseThrow(() -> new AnnualUpfrontRequestNotFoundException(command.requestId()));
    }

    @Override
    public AnnualUpfrontCashPaymentResult processOperation(RegisterAnnualUpfrontCashPaymentCommand command) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        Long invoiceId = annualUpfrontRequests.findInvoiceIdOfRequest(command.requestId()).orElseThrow(() -> new AnnualUpfrontRequestNotFoundException(command.requestId()));
        Invoice unlocked = invoiceQueries.findInvoicesByIds(List.of(invoiceId)).get(0);
        StudentCurrentAccount account = accountService.lockStudentCurrentAccount(unlocked.getStudentCurrentAccountId());
        if (!account.getStudentId().equals(command.studentId())) {
            throw new AnnualUpfrontRequestNotFoundException(command.requestId());
        }
        invoiceQueries.lockOpenInvoicesOfAccountInIdOrder(account.getId());
        AnnualUpfrontRequestPaymentFacts facts = annualUpfrontRequests.lockInvoiceAndFindItsRequest(invoiceId)
                .orElseThrow(() -> new AnnualUpfrontRequestNotFoundException(command.requestId()));
        Invoice annual = invoiceQueries.findInvoicesByIds(List.of(invoiceId)).get(0);
        boolean awaiting = facts.isAwaitingPayment() && annual.getDocumentStatus() == InvoiceDocumentStatus.AWAITING_PAYMENT;
        ReceiptInvoiceFact fact = new ReceiptInvoiceFact(annual.getId(), annual.getStudentCurrentAccountId(), annual.getCurrency(), false, annual.getPaymentStatus(), null,
                annual.getAmounts().total().value(), false, awaiting, annual.getPaymentDeadline());
        annualUpfrontPolicies.evaluateRulesUntilFirstViolation(new AnnualUpfrontReceiptCheck(List.of(fact), facts.isDiscarded(), facts.isPaymentAlreadyInitiated(), command.amount(),
                now, today)).throwIfDenied(BankTransferReceiptRejections::rejectionOf);
        NonNegativeMoney amount = NonNegativeMoney.of(command.amount());
        Payment payment = payments.save(Payment.registerApprovedCashPayment(account, amount, today, new CashPaymentDetails(textOrNull(command.receiptNumber())),
                command.idempotencyKey(), command.actor()));
        accountService.recordPaymentReceivedCredit(account, payment.getId(), amount, today, "Pago en efectivo del anual " + payment.getId());
        annualUpfrontRequests.issueInvoiceAndConfirmCashPayment(invoiceId, payment.getId(), account.getStudentId(), today, command.actor());
        accountService.recordInvoiceIssuedDebit(account, annual, today);
        allocationService.allocateAccountCreditSettlingProgramAdmissionInvoicesFirst(account, today, command.actor(), Set.of(invoiceId));
        log.info("Pago anual en efectivo registrado: pago={}, cuenta={}, pedido={}, factura={}, monto={}, actor={}", payment.getId(), account.getId(), command.requestId(), invoiceId,
                amount.value().amount(), command.actor().personId());
        return new AnnualUpfrontCashPaymentResult(payment.getId(), account.getId(), command.requestId(), invoiceId, amount, today);
    }

    @Override
    public List<? extends DomainEvent> domainEventsOf(RegisterAnnualUpfrontCashPaymentCommand command, AnnualUpfrontCashPaymentResult result) {
        return List.of(PaymentApprovedEvent.approvedFor(result.paymentId(), result.studentCurrentAccountId(), result.paidOn(), result.amount()));
    }

    private static String textOrNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
