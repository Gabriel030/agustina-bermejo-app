package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.operation.BillingOperation;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.billing.payment.domain.Refund;
import com.red_escuela.backend.billing.payment.domain.RefundEvent;
import com.red_escuela.backend.billing.payment.exception.RefundNotFoundException;
import com.red_escuela.backend.billing.payment.policy.RefundFailureCheck;
import com.red_escuela.backend.billing.payment.policy.RefundRejections;
import com.red_escuela.backend.billing.payment.repository.RefundRepository;
import com.red_escuela.backend.billing.payment.service.ReleasedRefundCreditAllocator;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import com.red_escuela.backend.shared.event.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FailRefundOperation implements BillingOperation<FailRefundCommand, Refund> {
    private static final Logger log = LoggerFactory.getLogger(FailRefundOperation.class);

    private final StudentCurrentAccountService accountService;
    private final RefundRepository refunds;
    private final PolicySet<RefundFailureCheck> failurePolicies;
    private final ReleasedRefundCreditAllocator releasedCreditAllocator;

    public FailRefundOperation(StudentCurrentAccountService accountService, RefundRepository refunds, PolicySet<RefundFailureCheck> refundFailurePolicySet,
                               ReleasedRefundCreditAllocator releasedCreditAllocator) {
        this.accountService = accountService;
        this.refunds = refunds;
        this.failurePolicies = refundFailurePolicySet;
        this.releasedCreditAllocator = releasedCreditAllocator;
    }

    @Override
    public BillingOperationType type() {
        return BillingOperationType.FAIL_REFUND;
    }

    @Override
    public Class<FailRefundCommand> contextType() {
        return FailRefundCommand.class;
    }

    @Override
    public List<String> validationMoments() {
        return List.of(failurePolicies.moment());
    }

    @Override
    public List<Class<? extends DomainEvent>> publishedEventTypes() {
        return List.of();
    }

    @Override
    public void requireValidContext(FailRefundCommand command) {
    }

    @Override
    public Refund processOperation(FailRefundCommand command) {
        Long accountId = refunds.findStudentCurrentAccountIdById(command.refundId()).orElseThrow(RefundNotFoundException::new);
        accountService.lockStudentCurrentAccount(accountId);
        Refund refund = refunds.findById(command.refundId()).orElseThrow(RefundNotFoundException::new);
        refund.requireTransitionBy(RefundEvent.REFUND_FAILED);
        failurePolicies.evaluateRulesUntilFirstViolation(new RefundFailureCheck(command.reason())).throwIfDenied(RefundRejections::rejectionOf);
        refund.fail(command.reason().strip(), command.actor());
        refunds.save(refund);
        releasedCreditAllocator.allocateAfterCommit(List.of(accountId), command.actor());
        log.warn("Devolución fallida: devolución={}, cuenta={}, motivo={}, actor={}", refund.getId(), accountId, refund.getFailureReason(), command.actor());
        return refund;
    }

    @Override
    public List<? extends DomainEvent> domainEventsOf(FailRefundCommand command, Refund result) {
        return List.of();
    }
}
