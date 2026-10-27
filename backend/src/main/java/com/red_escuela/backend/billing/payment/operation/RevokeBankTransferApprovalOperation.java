package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.billing.account.domain.AccountCreditAllocationResult;
import com.red_escuela.backend.billing.account.service.CurrentAccountAllocationService;
import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.events.AccountCreditAllocatedEvent;
import com.red_escuela.backend.billing.events.BankTransferApprovalRevokedEvent;
import com.red_escuela.backend.billing.events.PaymentAllocationReversedEvent;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.event.InvoicePaidDomainEvent;
import com.red_escuela.backend.billing.operation.AnnulledLateFeeReinstatement;
import com.red_escuela.backend.billing.operation.BillingOperation;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.billing.payment.domain.Payment;
import com.red_escuela.backend.billing.payment.domain.PaymentReversalResult;
import com.red_escuela.backend.billing.payment.exception.PaymentNotFoundException;
import com.red_escuela.backend.billing.payment.policy.PaymentRejectionCheck;
import com.red_escuela.backend.billing.payment.policy.PaymentReversalCheck;
import com.red_escuela.backend.billing.payment.policy.PaymentReversalRejections;
import com.red_escuela.backend.billing.payment.policy.PaymentReviewRejections;
import com.red_escuela.backend.billing.payment.repository.PaymentRepository;
import com.red_escuela.backend.billing.payment.service.PaymentPendingRefunds;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import com.red_escuela.backend.shared.event.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Component
public class RevokeBankTransferApprovalOperation implements BillingOperation<RevokeBankTransferApprovalCommand, PaymentReversalResult> {
    private static final Logger log = LoggerFactory.getLogger(RevokeBankTransferApprovalOperation.class);

    private final StudentCurrentAccountService accountService;
    private final CurrentAccountAllocationService allocationService;
    private final PaymentRepository payments;
    private final PaymentCreditReversal creditReversal;
    private final PolicySet<PaymentRejectionCheck> reasonPolicies;
    private final PolicySet<PaymentReversalCheck> revocationPolicies;
    private final AnnulledLateFeeReinstatement lateFeeReinstatement;
    private final PaymentPendingRefunds pendingRefunds;
    private final Clock clock;

    public RevokeBankTransferApprovalOperation(StudentCurrentAccountService accountService, CurrentAccountAllocationService allocationService, PaymentRepository payments,
                                               PaymentCreditReversal creditReversal, PolicySet<PaymentRejectionCheck> paymentRejectionReasonPolicySet,
                                               PolicySet<PaymentReversalCheck> paymentApprovalRevocationPolicySet, AnnulledLateFeeReinstatement lateFeeReinstatement, PaymentPendingRefunds pendingRefunds, Clock clock) {
        this.accountService = accountService;
        this.allocationService = allocationService;
        this.payments = payments;
        this.creditReversal = creditReversal;
        this.reasonPolicies = paymentRejectionReasonPolicySet;
        this.revocationPolicies = paymentApprovalRevocationPolicySet;
        this.lateFeeReinstatement = lateFeeReinstatement;
        this.pendingRefunds = pendingRefunds;
        this.clock = clock;
    }

    @Override
    public BillingOperationType type() {
        return BillingOperationType.REVOKE_BANK_TRANSFER_APPROVAL;
    }

    @Override
    public Class<RevokeBankTransferApprovalCommand> contextType() {
        return RevokeBankTransferApprovalCommand.class;
    }

    @Override
    public List<String> validationMoments() {
        return List.of(reasonPolicies.moment(), revocationPolicies.moment());
    }

    @Override
    public List<Class<? extends DomainEvent>> publishedEventTypes() {
        return List.of(BankTransferApprovalRevokedEvent.class, PaymentAllocationReversedEvent.class, AccountCreditAllocatedEvent.class, InvoicePaidDomainEvent.class);
    }

    @Override
    public void requireValidContext(RevokeBankTransferApprovalCommand command) {
        reasonPolicies.evaluateRulesUntilFirstViolation(new PaymentRejectionCheck(command.reason())).throwIfDenied(PaymentReviewRejections::rejectionOf);
    }

    @Override
    public PaymentReversalResult processOperation(RevokeBankTransferApprovalCommand command) {
        LocalDate today = LocalDate.now(clock);
        Long accountId = payments.findStudentCurrentAccountIdById(command.paymentId()).orElseThrow(PaymentNotFoundException::new);
        StudentCurrentAccount account = accountService.lockStudentCurrentAccount(accountId);
        Payment payment = payments.findById(command.paymentId()).orElseThrow(PaymentNotFoundException::new);
        revocationPolicies.evaluateRulesUntilFirstViolation(PaymentReversalCheck.ofRevocation(payment.creditedAmount().value(), payment.getRefundedAmount().value(), pendingRefunds.pendingAmountOf(payment)))
                .throwIfDenied(PaymentReversalRejections::rejectionOf);
        payment.revokeBankTransferApproval(command.reason(), command.reviewer());
        payments.save(payment);
        String concept = "Revocación de la aprobación del pago " + payment.getId();
        PaymentCreditReversal.ReversedPaymentAllocations reversed = creditReversal.reverseAllocationsOf(account, payment, today, concept, command.reviewer());
        accountService.recordPaymentReversalDebit(account, reversed.credit(), today, concept);
        lateFeeReinstatement.reinstateLateFeesAnnulledByTheReversedPayment(reversed.invoicesReopened(), today);
        AccountCreditAllocationResult reallocation = allocationService.allocateAccountCreditToOldestInvoices(account, today, command.reviewer());
        log.warn("Aprobación de transferencia revocada: pago={}, cuenta={}, monto={}, facturasReabiertas={}, actor={}", payment.getId(), accountId,
                reversed.credit().getAmount().value().amount(), reversed.invoicesReopened(), command.reviewer());
        return new PaymentReversalResult(payment.getId(), null, accountId, payment.getStatus(), reversed.credit().getAmount(), null, today, null, null,
                reversed.invoicesReopened(), reallocation.settledInvoices().stream().map(Invoice::getId).toList());
    }

    @Override
    public List<? extends DomainEvent> domainEventsOf(RevokeBankTransferApprovalCommand command, PaymentReversalResult result) {
        return List.of(BankTransferApprovalRevokedEvent.revokedFor(result.paymentId(), result.studentCurrentAccountId(), result.effectiveDate(), result.amount()));
    }
}
