package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.billing.events.RefundApprovedEvent;
import com.red_escuela.backend.billing.operation.BillingOperation;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.billing.payment.domain.Refund;
import com.red_escuela.backend.billing.payment.policy.RefundAvailabilityCheck;
import com.red_escuela.backend.billing.payment.policy.RefundDestinationCheck;
import com.red_escuela.backend.billing.payment.policy.RefundRejections;
import com.red_escuela.backend.billing.payment.policy.RefundRequestCheck;
import com.red_escuela.backend.billing.payment.repository.RefundRepository;
import com.red_escuela.backend.billing.payment.service.PaymentActorResolver;
import com.red_escuela.backend.billing.payment.service.RefundRequestRecorder;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import com.red_escuela.backend.shared.event.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Component
public class IssueRefundOperation implements BillingOperation<IssueRefundCommand, List<Refund>> {
    private static final Logger log = LoggerFactory.getLogger(IssueRefundOperation.class);

    private final RefundRequestRecorder recorder;
    private final RefundRepository refunds;
    private final PaymentActorResolver actorResolver;
    private final PolicySet<RefundRequestCheck> requestFormatPolicies;
    private final PolicySet<RefundAvailabilityCheck> availabilityPolicies;
    private final PolicySet<RefundDestinationCheck> destinationPolicies;
    private final Clock clock;

    public IssueRefundOperation(RefundRequestRecorder recorder, RefundRepository refunds, PaymentActorResolver actorResolver,
                                PolicySet<RefundRequestCheck> refundRequestFormatPolicySet, PolicySet<RefundAvailabilityCheck> refundAvailabilityPolicySet,
                                PolicySet<RefundDestinationCheck> refundDestinationPolicySet, Clock clock) {
        this.recorder = recorder;
        this.refunds = refunds;
        this.actorResolver = actorResolver;
        this.requestFormatPolicies = refundRequestFormatPolicySet;
        this.availabilityPolicies = refundAvailabilityPolicySet;
        this.destinationPolicies = refundDestinationPolicySet;
        this.clock = clock;
    }

    @Override
    public BillingOperationType type() {
        return BillingOperationType.ISSUE_REFUND;
    }

    @Override
    public Class<IssueRefundCommand> contextType() {
        return IssueRefundCommand.class;
    }

    @Override
    public List<String> validationMoments() {
        return List.of(requestFormatPolicies.moment(), availabilityPolicies.moment(), destinationPolicies.moment());
    }

    @Override
    public List<Class<? extends DomainEvent>> publishedEventTypes() {
        return List.of(RefundApprovedEvent.class);
    }

    @Override
    public void requireValidContext(IssueRefundCommand command) {
        requestFormatPolicies.evaluateRulesUntilFirstViolation(new RefundRequestCheck(command.amount(), command.requestedDestination()))
                .throwIfDenied(RefundRejections::rejectionOf);
    }

    @Override
    public List<Refund> processOperation(IssueRefundCommand command) {
        List<Refund> parts = command.authorizationRequestId() == null
                ? recorder.recordRequestedPartsOf(command.studentId(), command.amount(), command.requestedDestination(), command.requestedByPersonId(), null)
                : recorder.lockAndRevalidateRequestedPartsOf(command.authorizationRequestId());
        LocalDate today = LocalDate.now(clock);
        StatusChangeActor approver = actorResolver.currentPersonAsActor();
        parts.forEach(part -> {
            part.approve(today, approver);
            refunds.save(part);
        });
        log.info("Devolución aprobada: pedido={}, partes={}, alumno={}, monto={}, aprobadaPor={}", parts.get(0).getRefundRequestId(), parts.size(), command.studentId(),
                command.amount().amount(), approver);
        return parts;
    }

    @Override
    public List<? extends DomainEvent> domainEventsOf(IssueRefundCommand command, List<Refund> result) {
        return result.stream()
                .map(refund -> RefundApprovedEvent.approvedFor(refund.getId(), refund.getRefundRequestId(), refund.getPaymentId(), refund.getStudentCurrentAccountId(),
                        refund.getAmount()))
                .toList();
    }
}
