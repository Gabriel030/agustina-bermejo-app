package com.red_escuela.backend.billing.payment.service;

import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.payment.domain.Payment;
import com.red_escuela.backend.billing.payment.domain.PaymentGatewayCode;
import com.red_escuela.backend.billing.payment.domain.PaymentStatus;
import com.red_escuela.backend.billing.payment.domain.RefundDestination;
import com.red_escuela.backend.billing.payment.domain.RefundEligibility;
import com.red_escuela.backend.billing.payment.domain.RefundPlanOutcome;
import com.red_escuela.backend.billing.payment.domain.RefundablePayment;
import com.red_escuela.backend.billing.payment.domain.RefundablePaymentView;
import com.red_escuela.backend.billing.payment.policy.RefundAvailabilityCheck;
import com.red_escuela.backend.billing.payment.policy.RefundRequestCheck;
import com.red_escuela.backend.billing.payment.repository.PaymentRepository;
import com.red_escuela.backend.identity.api.StudentExistenceLookup;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.policy.PolicyEvaluationResult;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RefundPreviewService {
    private static final BigDecimal SMALLEST_REFUNDABLE_AMOUNT = new BigDecimal("0.01");
    private static final String DESTINATION_REQUIRED = "REFUND_DESTINATION_REQUIRED";
    private static final PolicyViolation NO_ACCOUNT_CREDIT = new PolicyViolation("INSUFFICIENT_ACCOUNT_CREDIT", "El alumno no tiene saldo a favor para devolver.");

    private final StudentExistenceLookup students;
    private final StudentCurrentAccountService accountService;
    private final RefundRequestRecorder recorder;
    private final PaymentRepository payments;
    private final PolicySet<RefundRequestCheck> requestFormatPolicies;
    private final PolicySet<RefundAvailabilityCheck> availabilityPolicies;
    private final Clock clock;

    public RefundPreviewService(StudentExistenceLookup students, StudentCurrentAccountService accountService, RefundRequestRecorder recorder, PaymentRepository payments,
                                PolicySet<RefundRequestCheck> refundRequestFormatPolicySet,
                                PolicySet<RefundAvailabilityCheck> refundAvailabilityPolicySet, Clock clock) {
        this.students = students;
        this.accountService = accountService;
        this.recorder = recorder;
        this.payments = payments;
        this.requestFormatPolicies = refundRequestFormatPolicySet;
        this.availabilityPolicies = refundAvailabilityPolicySet;
        this.clock = clock;
    }

    public RefundPlanOutcome previewRefundOfExistingStudent(Long studentId, Money amount, RefundDestination requestedDestination) {
        students.requireExistingStudent(studentId);
        return previewRefundOf(studentId, amount, requestedDestination);
    }

    public RefundPlanOutcome previewRefundOf(Long studentId, Money amount, RefundDestination requestedDestination) {
        PolicyEvaluationResult format = requestFormatPolicies.evaluateRulesUntilFirstViolation(new RefundRequestCheck(amount, requestedDestination));
        boolean onlyDestinationMissing = !format.allowed() && format.violations().stream().allMatch(violation -> DESTINATION_REQUIRED.equals(violation.code()));
        if (!format.allowed() && !onlyDestinationMissing) {
            return RefundPlanOutcome.blocked(format.violations());
        }
        RefundPlanOutcome plan = accountService.findStudentCurrentAccount(studentId, amount.currency())
                .map(account -> recorder.evaluatePlanFor(account, amount, requestedDestination, null, LocalDate.now(clock)))
                .orElseGet(() -> RefundPlanOutcome.blocked(List.of(NO_ACCOUNT_CREDIT)));
        return plan.isAllowed() && onlyDestinationMissing ? RefundPlanOutcome.plannedButBlocked(plan.parts(), format.violations()) : plan;
    }

    public RefundEligibility describeEligibilityOfExistingStudent(Long studentId, Currency currency) {
        students.requireExistingStudent(studentId);
        return describeEligibilityOf(studentId, currency);
    }

    public RefundEligibility describeEligibilityOf(Long studentId, Currency currency) {
        return accountService.findStudentCurrentAccount(studentId, currency)
                .map(this::describeEligibilityOfAccount)
                .orElseGet(() -> RefundEligibility.withoutAccount(currency, NO_ACCOUNT_CREDIT));
    }

    private RefundEligibility describeEligibilityOfAccount(StudentCurrentAccount account) {
        Currency currency = account.getCurrency();
        LocalDate today = LocalDate.now(clock);
        RefundAvailabilityCheck probe = recorder.availabilityCheckFor(account, Money.of(SMALLEST_REFUNDABLE_AMOUNT, currency), null, today);
        PolicyViolation blockedBy = availabilityPolicies.evaluateRulesUntilFirstViolation(probe).violations().stream().findFirst().orElse(null);
        return new RefundEligibility(currency, probe.unallocatedCredit(), probe.pendingRefundAmount(), probe.availableCredit(), probe.hasOverdueDebt(), blockedBy,
                refundablePaymentViewsOf(account));
    }

    private List<RefundablePaymentView> refundablePaymentViewsOf(StudentCurrentAccount account) {
        Map<Long, RefundablePayment> refundableById = recorder.refundablePaymentsOf(account, RefundDestination.none(), null).stream()
                .collect(Collectors.toMap(RefundablePayment::paymentId, Function.identity()));
        return payments.findByStudentCurrentAccountIdAndGatewayAndStatus(account.getId(), PaymentGatewayCode.MANUAL, PaymentStatus.APPROVED).stream()
                .filter(payment -> refundableById.containsKey(payment.getId()))
                .sorted(Comparator.comparing(Payment::getPaidOn).thenComparing(Payment::getId).reversed())
                .map(payment -> viewOf(payment, refundableById.get(payment.getId())))
                .toList();
    }

    private RefundablePaymentView viewOf(Payment payment, RefundablePayment refundable) {
        Money pending = payment.creditedAmount().value().minus(payment.getRefundedAmount().value()).minus(refundable.refundable().value());
        NonNegativeMoney pendingNow = pending.isNegative() ? NonNegativeMoney.zero(payment.getCurrency()) : NonNegativeMoney.of(pending);
        return new RefundablePaymentView(payment.getId(), payment.getPaidOn(), payment.getMethodType(), payment.creditedAmount(), payment.getRefundedAmount(), pendingNow,
                refundable.refundable(), refundable.destination());
    }
}
