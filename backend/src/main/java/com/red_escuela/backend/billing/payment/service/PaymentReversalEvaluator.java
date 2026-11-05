package com.red_escuela.backend.billing.payment.service;

import com.red_escuela.backend.billing.payment.domain.Chargeback;
import com.red_escuela.backend.billing.payment.domain.Payment;
import com.red_escuela.backend.billing.payment.domain.PaymentEvent;
import com.red_escuela.backend.billing.payment.exception.IllegalPaymentStatusTransitionException;
import com.red_escuela.backend.billing.payment.policy.PaymentRejectionCheck;
import com.red_escuela.backend.billing.payment.policy.PaymentReversalCheck;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

@Component
class PaymentReversalEvaluator {
    private static final PolicyViolation ILLEGAL_TRANSITION = new PolicyViolation("ILLEGAL_PAYMENT_STATUS_TRANSITION",
            "El pago no está en un estado que admita esta acción.");
    private static final PolicyViolation CHARGEBACK_ALREADY_REVERSED = new PolicyViolation("CHARGEBACK_ALREADY_REVERSED", "El contracargo ya fue revertido.");

    private final PolicySet<PaymentRejectionCheck> reasonPolicies;
    private final PolicySet<PaymentReversalCheck> revocationPolicies;
    private final PolicySet<PaymentReversalCheck> chargebackPolicies;

    PaymentReversalEvaluator(PolicySet<PaymentRejectionCheck> paymentRejectionReasonPolicySet, PolicySet<PaymentReversalCheck> paymentApprovalRevocationPolicySet,
                             PolicySet<PaymentReversalCheck> chargebackRegistrationPolicySet) {
        this.reasonPolicies = paymentRejectionReasonPolicySet;
        this.revocationPolicies = paymentApprovalRevocationPolicySet;
        this.chargebackPolicies = chargebackRegistrationPolicySet;
    }

    Optional<PolicyViolation> findChargebackRegistrationViolation(Payment payment, Money credit, Money pendingRefundAmount, Money requestedAmount,
                                                                  LocalDate chargedBackOn, String reference, LocalDate today) {
        Optional<PolicyViolation> transition = findTransitionViolation(payment, PaymentEvent.CHARGEBACK_REGISTERED);
        if (transition.isPresent()) {
            return transition;
        }
        return chargebackPolicies.evaluateRulesUntilFirstViolation(new PaymentReversalCheck(credit, payment.getRefundedAmount().value(), pendingRefundAmount,
                requestedAmount, payment.getPaidOn(), chargedBackOn, today, reference)).violations().stream().findFirst();
    }

    Optional<PolicyViolation> findChargebackReversalViolation(Chargeback chargeback, Payment payment) {
        if (chargeback.isReversed()) {
            return Optional.of(CHARGEBACK_ALREADY_REVERSED);
        }
        return findTransitionViolation(payment, PaymentEvent.CHARGEBACK_REVERSED);
    }

    Optional<PolicyViolation> findApprovalRevocationViolation(Payment payment, Money pendingRefundAmount, String reason) {
        Optional<PolicyViolation> reasonViolation = reasonPolicies.evaluateRulesUntilFirstViolation(new PaymentRejectionCheck(reason)).violations().stream().findFirst();
        if (reasonViolation.isPresent()) {
            return reasonViolation;
        }
        Optional<PolicyViolation> policyViolation = revocationPolicies.evaluateRulesUntilFirstViolation(
                PaymentReversalCheck.ofRevocation(payment.creditedAmount().value(), payment.getRefundedAmount().value(), pendingRefundAmount)).violations().stream().findFirst();
        if (policyViolation.isPresent()) {
            return policyViolation;
        }
        return findTransitionViolation(payment, PaymentEvent.BANK_TRANSFER_APPROVAL_REVOKED);
    }

    private Optional<PolicyViolation> findTransitionViolation(Payment payment, PaymentEvent event) {
        try {
            payment.requireAllows(event);
            return Optional.empty();
        } catch (IllegalPaymentStatusTransitionException rejected) {
            return Optional.of(ILLEGAL_TRANSITION);
        }
    }
}
