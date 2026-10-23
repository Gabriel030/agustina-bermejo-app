package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.billing.account.domain.AccountCreditAllocationResult;
import com.red_escuela.backend.billing.account.domain.CurrentAccountEntry;
import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.billing.account.service.CurrentAccountAllocationService;
import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.events.AccountCreditAllocatedEvent;
import com.red_escuela.backend.billing.events.PaymentAllocationReversedEvent;
import com.red_escuela.backend.billing.events.PaymentChargedBackEvent;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.event.InvoicePaidDomainEvent;
import com.red_escuela.backend.billing.operation.AnnulledLateFeeReinstatement;
import com.red_escuela.backend.billing.operation.BillingOperation;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.billing.payment.domain.Chargeback;
import com.red_escuela.backend.billing.payment.domain.Payment;
import com.red_escuela.backend.billing.payment.domain.PaymentReversalResult;
import com.red_escuela.backend.billing.payment.exception.PaymentNotFoundException;
import com.red_escuela.backend.billing.payment.policy.PaymentReversalCheck;
import com.red_escuela.backend.billing.payment.policy.PaymentReversalRejections;
import com.red_escuela.backend.billing.payment.repository.ChargebackRepository;
import com.red_escuela.backend.billing.payment.repository.PaymentRepository;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import com.red_escuela.backend.shared.event.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Component
public class RegisterChargebackOperation implements BillingOperation<RegisterChargebackCommand, PaymentReversalResult> {
    private static final Logger log = LoggerFactory.getLogger(RegisterChargebackOperation.class);

    private final StudentCurrentAccountService accountService;
    private final CurrentAccountAllocationService allocationService;
    private final PaymentRepository payments;
    private final ChargebackRepository chargebacks;
    private final PaymentCreditReversal creditReversal;
    private final PolicySet<PaymentReversalCheck> chargebackPolicies;
    private final AnnulledLateFeeReinstatement lateFeeReinstatement;
    private final Clock clock;

    public RegisterChargebackOperation(StudentCurrentAccountService accountService, CurrentAccountAllocationService allocationService, PaymentRepository payments,
                                       ChargebackRepository chargebacks, PaymentCreditReversal creditReversal, PolicySet<PaymentReversalCheck> chargebackRegistrationPolicySet,
                                       AnnulledLateFeeReinstatement lateFeeReinstatement, Clock clock) {
        this.accountService = accountService;
        this.allocationService = allocationService;
        this.payments = payments;
        this.chargebacks = chargebacks;
        this.creditReversal = creditReversal;
        this.chargebackPolicies = chargebackRegistrationPolicySet;
        this.lateFeeReinstatement = lateFeeReinstatement;
        this.clock = clock;
    }

    @Override
    public BillingOperationType type() {
        return BillingOperationType.REGISTER_CHARGEBACK;
    }

    @Override
    public Class<RegisterChargebackCommand> contextType() {
        return RegisterChargebackCommand.class;
    }

    @Override
    public List<String> validationMoments() {
        return List.of(chargebackPolicies.moment());
    }

    @Override
    public List<Class<? extends DomainEvent>> publishedEventTypes() {
        return List.of(PaymentChargedBackEvent.class, PaymentAllocationReversedEvent.class, AccountCreditAllocatedEvent.class, InvoicePaidDomainEvent.class);
    }

    @Override
    public void requireValidContext(RegisterChargebackCommand command) {
    }

    @Override
    public PaymentReversalResult processOperation(RegisterChargebackCommand command) {
        LocalDate today = LocalDate.now(clock);
        Long accountId = payments.findStudentCurrentAccountIdById(command.paymentId()).orElseThrow(PaymentNotFoundException::new);
        StudentCurrentAccount account = accountService.lockStudentCurrentAccount(accountId);
        Payment payment = payments.findById(command.paymentId()).orElseThrow(PaymentNotFoundException::new);
        payment.registerChargeback(command.reference(), command.actor());
        CurrentAccountEntry credit = accountService.findPaymentCreditNotYetReversed(payment.getId())
                .orElseThrow(() -> new IllegalStateException("The payment " + payment.getId() + " has no credit in the current account to reverse"));
        chargebackPolicies.evaluateRulesUntilFirstViolation(new PaymentReversalCheck(credit.getAmount().value(), payment.getRefundedAmount().value(), command.amount(),
                payment.getPaidOn(), command.chargedBackOn(), today, command.reference())).throwIfDenied(PaymentReversalRejections::rejectionOf);
        payments.save(payment);
        Chargeback chargeback = chargebacks.save(Chargeback.registerFor(payment, credit.getAmount(), command.chargedBackOn(), command.reference(), command.actor()));
        String concept = "Contracargo del pago " + payment.getId();
        PaymentCreditReversal.ReversedPaymentAllocations reversed = creditReversal.reverseAllocationsOf(account, payment, today, concept, command.actor());
        accountService.recordPaymentReversalDebit(account, reversed.credit(), command.chargedBackOn(), concept);
        lateFeeReinstatement.reinstateLateFeesAnnulledByTheReversedPayment(reversed.invoicesReopened(), today);
        AccountCreditAllocationResult reallocation = allocationService.allocateAccountCreditToOldestInvoices(account, today, command.actor());
        log.warn("Contracargo registrado: pago={}, contracargo={}, cuenta={}, monto={}, fechaDelContracargo={}, facturasReabiertas={}, actor={}", payment.getId(),
                chargeback.getId(), accountId, reversed.credit().getAmount().value().amount(), command.chargedBackOn(), reversed.invoicesReopened(), command.actor());
        return new PaymentReversalResult(payment.getId(), chargeback.getId(), accountId, payment.getStatus(), reversed.credit().getAmount(), command.chargedBackOn(), today, null,
                command.reference(), reversed.invoicesReopened(), reallocation.settledInvoices().stream().map(Invoice::getId).toList());
    }

    @Override
    public List<? extends DomainEvent> domainEventsOf(RegisterChargebackCommand command, PaymentReversalResult result) {
        return List.of(PaymentChargedBackEvent.chargedBackFor(result.paymentId(), result.chargebackId(), result.studentCurrentAccountId(), result.chargedBackOn(),
                result.amount(), result.reference()));
    }
}
