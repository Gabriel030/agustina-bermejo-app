package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.events.RefundCompletedEvent;
import com.red_escuela.backend.billing.operation.BillingOperation;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.billing.payment.domain.Payment;
import com.red_escuela.backend.billing.payment.domain.Refund;
import com.red_escuela.backend.billing.payment.domain.RefundEvent;
import com.red_escuela.backend.billing.payment.exception.PaymentNotFoundException;
import com.red_escuela.backend.billing.payment.exception.RefundNotFoundException;
import com.red_escuela.backend.billing.payment.policy.RefundCompletionCheck;
import com.red_escuela.backend.billing.payment.policy.RefundRejections;
import com.red_escuela.backend.billing.payment.repository.PaymentRepository;
import com.red_escuela.backend.billing.payment.repository.RefundRepository;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import com.red_escuela.backend.shared.event.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Component
public class CompleteRefundOperation implements BillingOperation<CompleteRefundCommand, Refund> {
    private static final Logger log = LoggerFactory.getLogger(CompleteRefundOperation.class);

    private final StudentCurrentAccountService accountService;
    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final PolicySet<RefundCompletionCheck> completionPolicies;
    private final Clock clock;

    public CompleteRefundOperation(StudentCurrentAccountService accountService, PaymentRepository payments, RefundRepository refunds,
                                   PolicySet<RefundCompletionCheck> refundCompletionPolicySet, Clock clock) {
        this.accountService = accountService;
        this.payments = payments;
        this.refunds = refunds;
        this.completionPolicies = refundCompletionPolicySet;
        this.clock = clock;
    }

    @Override
    public BillingOperationType type() {
        return BillingOperationType.COMPLETE_REFUND;
    }

    @Override
    public Class<CompleteRefundCommand> contextType() {
        return CompleteRefundCommand.class;
    }

    @Override
    public List<String> validationMoments() {
        return List.of(completionPolicies.moment());
    }

    @Override
    public List<Class<? extends DomainEvent>> publishedEventTypes() {
        return List.of(RefundCompletedEvent.class);
    }

    @Override
    public void requireValidContext(CompleteRefundCommand command) {
    }

    @Override
    public Refund processOperation(CompleteRefundCommand command) {
        LocalDate today = LocalDate.now(clock);
        Long accountId = refunds.findStudentCurrentAccountIdById(command.refundId()).orElseThrow(RefundNotFoundException::new);
        StudentCurrentAccount account = accountService.lockStudentCurrentAccount(accountId);
        Refund refund = refunds.findById(command.refundId()).orElseThrow(RefundNotFoundException::new);
        refund.requireTransitionBy(RefundEvent.REFUND_COMPLETED);
        Payment payment = payments.findById(refund.getPaymentId()).orElseThrow(PaymentNotFoundException::new);
        completionPolicies.evaluateRulesUntilFirstViolation(new RefundCompletionCheck(refund.getMethodType(), command.methodType(), command.completedOn(),
                refund.getRequestedOn(), today, command.reference())).throwIfDenied(RefundRejections::rejectionOf);
        String reference = command.reference().strip();
        String concept = "Devolución " + refund.getId() + " del pago " + payment.getId();
        accountService.recordRefundPaidDebit(account, refund.getId(), refund.getAmount(), command.completedOn(), concept);
        payment.registerRefundedAmount(refund.getAmount(), command.actor(), concept);
        refund.complete(command.completedOn(), reference, command.actor());
        payments.save(payment);
        refunds.save(refund);
        log.info("Devolución completada: devolución={}, pago={}, cuenta={}, monto={}, fecha={}, actor={}", refund.getId(), payment.getId(), accountId,
                refund.getAmount().value().amount(), command.completedOn(), command.actor());
        return refund;
    }

    @Override
    public List<? extends DomainEvent> domainEventsOf(CompleteRefundCommand command, Refund result) {
        return List.of(RefundCompletedEvent.completedFor(result.getId(), result.getRefundRequestId(), result.getPaymentId(), result.getStudentCurrentAccountId(),
                result.getAmount(), result.getCompletedOn(), result.getCompletionReference()));
    }
}
