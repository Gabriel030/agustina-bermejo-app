package com.red_escuela.backend.billing.payment.service;

import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.billing.account.exception.InsufficientAccountCreditException;
import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.invoice.service.InvoiceFollowUpQueries;
import com.red_escuela.backend.billing.payment.domain.BankTransferPaymentDetails;
import com.red_escuela.backend.billing.payment.domain.NewestPaymentFirstRefundSplitter;
import com.red_escuela.backend.billing.payment.domain.Payment;
import com.red_escuela.backend.billing.payment.domain.PaymentGatewayCode;
import com.red_escuela.backend.billing.payment.domain.PaymentMethodType;
import com.red_escuela.backend.billing.payment.domain.PaymentStatus;
import com.red_escuela.backend.billing.payment.domain.Refund;
import com.red_escuela.backend.billing.payment.domain.RefundDestination;
import com.red_escuela.backend.billing.payment.domain.RefundPart;
import com.red_escuela.backend.billing.payment.domain.RefundPlanOutcome;
import com.red_escuela.backend.billing.payment.domain.RefundStatus;
import com.red_escuela.backend.billing.payment.domain.RefundablePayment;
import com.red_escuela.backend.billing.payment.exception.RefundNotFoundException;
import com.red_escuela.backend.billing.payment.policy.RefundAvailabilityCheck;
import com.red_escuela.backend.billing.payment.policy.RefundDestinationCheck;
import com.red_escuela.backend.billing.payment.policy.RefundRejections;
import com.red_escuela.backend.billing.payment.repository.PaymentRepository;
import com.red_escuela.backend.billing.payment.repository.RefundRepository;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.policy.PolicyEvaluationResult;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class RefundRequestRecorder {
    private static final Set<PaymentMethodType> REFUNDABLE_MANUAL_METHODS = Set.of(PaymentMethodType.CASH, PaymentMethodType.BANK_TRANSFER);

    private final StudentCurrentAccountService accountService;
    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final InvoiceFollowUpQueries invoices;
    private final PolicySet<RefundAvailabilityCheck> availabilityPolicies;
    private final PolicySet<RefundDestinationCheck> destinationPolicies;
    private final Clock clock;

    public RefundRequestRecorder(StudentCurrentAccountService accountService, PaymentRepository payments, RefundRepository refunds, InvoiceFollowUpQueries invoices,
                                 PolicySet<RefundAvailabilityCheck> refundAvailabilityPolicySet, PolicySet<RefundDestinationCheck> refundDestinationPolicySet, Clock clock) {
        this.accountService = accountService;
        this.payments = payments;
        this.refunds = refunds;
        this.invoices = invoices;
        this.availabilityPolicies = refundAvailabilityPolicySet;
        this.destinationPolicies = refundDestinationPolicySet;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<Refund> recordRequestedPartsOf(Long studentId, Money amount, RefundDestination requestedDestination, Long requestedByPersonId, Long authorizationRequestId) {
        StudentCurrentAccount account = lockAccountOf(studentId, amount.currency());
        LocalDate today = LocalDate.now(clock);
        List<RefundPart> parts = planPartsFor(account, amount, requestedDestination, null, today);
        Long refundRequestId = refunds.nextRefundRequestId();
        return parts.stream()
                .map(part -> refunds.save(Refund.requestPartOf(refundRequestId, account.getId(), part, requestedByPersonId, authorizationRequestId, today)))
                .toList();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<Refund> lockAndRevalidateRequestedPartsOf(Long authorizationRequestId) {
        Long accountId = refunds.findByAuthorizationRequestIdOrderByIdAsc(authorizationRequestId).stream().findFirst()
                .orElseThrow(RefundNotFoundException::new).getStudentCurrentAccountId();
        StudentCurrentAccount account = accountService.lockStudentCurrentAccount(accountId);
        List<Refund> requested = refunds.findByAuthorizationRequestIdAndStatusOrderByIdAsc(authorizationRequestId, RefundStatus.REQUESTED);
        Refund.requireAnyRequestedPart(requested);
        Money total = requested.stream().map(refund -> refund.getAmount().value()).reduce(Money.zero(account.getCurrency()), Money::plus);
        availabilityPolicies.evaluateRulesUntilFirstViolation(availabilityCheckFor(account, total, authorizationRequestId, LocalDate.now(clock)))
                .throwIfDenied(RefundRejections::rejectionOf);
        return requested;
    }

    private StudentCurrentAccount lockAccountOf(Long studentId, Currency currency) {
        Long accountId = accountService.findStudentCurrentAccount(studentId, currency).map(StudentCurrentAccount::getId)
                .orElseThrow(InsufficientAccountCreditException::new);
        return accountService.lockStudentCurrentAccount(accountId);
    }

    private List<RefundPart> planPartsFor(StudentCurrentAccount account, Money amount, RefundDestination requestedDestination, Long excludedAuthorizationRequestId,
                                          LocalDate today) {
        return evaluatePlanFor(account, amount, requestedDestination, excludedAuthorizationRequestId, today).partsOrThrow(RefundRejections::rejectionOf);
    }

    RefundPlanOutcome evaluatePlanFor(StudentCurrentAccount account, Money amount, RefundDestination requestedDestination, Long excludedAuthorizationRequestId,
                                      LocalDate today) {
        RefundAvailabilityCheck availability = availabilityCheckFor(account, amount, excludedAuthorizationRequestId, today);
        PolicyEvaluationResult availabilityResult = availabilityPolicies.evaluateRulesUntilFirstViolation(availability);
        if (!availabilityResult.allowed()) {
            return RefundPlanOutcome.blocked(availabilityResult.violations());
        }
        List<RefundablePayment> refundable = refundablePaymentsOf(account, requestedDestination, excludedAuthorizationRequestId);
        List<RefundPart> parts = NewestPaymentFirstRefundSplitter.splitAmountAcross(amount, refundable);
        PolicyEvaluationResult destinationResult = destinationPolicies
                .evaluateRulesUntilFirstViolation(new RefundDestinationCheck(parts.stream().map(RefundPart::destination).toList()));
        return destinationResult.allowed() ? RefundPlanOutcome.planned(parts) : RefundPlanOutcome.plannedButBlocked(parts, destinationResult.violations());
    }

    RefundAvailabilityCheck availabilityCheckFor(StudentCurrentAccount account, Money amount, Long excludedAuthorizationRequestId, LocalDate today) {
        Currency currency = account.getCurrency();
        NonNegativeMoney pending = pendingAmountsByPayment(account.getId(), excludedAuthorizationRequestId).values().stream()
                .reduce(NonNegativeMoney.zero(currency), NonNegativeMoney::plus);
        NonNegativeMoney refundable = refundablePaymentsOf(account, RefundDestination.none(), excludedAuthorizationRequestId).stream().map(RefundablePayment::refundable)
                .reduce(NonNegativeMoney.zero(currency), NonNegativeMoney::plus);
        return new RefundAvailabilityCheck(amount, invoices.hasUnpaidIssuedDocumentOverdue(account.getId(), today), account.getUnallocatedCreditAmount(), pending,
                refundable);
    }

    List<RefundablePayment> refundablePaymentsOf(StudentCurrentAccount account, RefundDestination requestedDestination, Long excludedAuthorizationRequestId) {
        Map<Long, NonNegativeMoney> pendingByPayment = pendingAmountsByPayment(account.getId(), excludedAuthorizationRequestId);
        return payments.findByStudentCurrentAccountIdAndGatewayAndStatus(account.getId(), PaymentGatewayCode.MANUAL, PaymentStatus.APPROVED).stream()
                .filter(payment -> REFUNDABLE_MANUAL_METHODS.contains(payment.getMethodType()))
                .map(payment -> new RefundablePayment(payment.getId(), payment.getPaidOn(), payment.getMethodType(),
                        refundableOf(payment, pendingByPayment.get(payment.getId())), destinationFor(payment, requestedDestination)))
                .toList();
    }

    private NonNegativeMoney refundableOf(Payment payment, NonNegativeMoney pending) {
        Money remaining = payment.creditedAmount().minus(payment.getRefundedAmount());
        if (pending != null) {
            remaining = remaining.minus(pending.value());
        }
        return remaining.isNegative() ? NonNegativeMoney.zero(payment.getCurrency()) : NonNegativeMoney.of(remaining);
    }

    private RefundDestination destinationFor(Payment payment, RefundDestination requestedDestination) {
        if (payment.getMethodDetails() instanceof BankTransferPaymentDetails transfer) {
            RefundDestination fromReceipt = new RefundDestination(transfer.originAccount(), transfer.holderName());
            if (fromReceipt.isComplete()) {
                return fromReceipt;
            }
        }
        return requestedDestination != null && requestedDestination.isComplete() ? requestedDestination : RefundDestination.none();
    }

    private Map<Long, NonNegativeMoney> pendingAmountsByPayment(Long accountId, Long excludedAuthorizationRequestId) {
        Map<Long, NonNegativeMoney> pending = new HashMap<>();
        refunds.findByStudentCurrentAccountIdAndStatusIn(accountId, List.of(RefundStatus.REQUESTED, RefundStatus.APPROVED)).stream()
                .filter(refund -> excludedAuthorizationRequestId == null || !excludedAuthorizationRequestId.equals(refund.getAuthorizationRequestId()))
                .forEach(refund -> pending.merge(refund.getPaymentId(), refund.getAmount(), NonNegativeMoney::plus));
        return pending;
    }
}
