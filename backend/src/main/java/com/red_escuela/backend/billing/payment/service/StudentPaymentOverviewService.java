package com.red_escuela.backend.billing.payment.service;

import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.payment.domain.Chargeback;
import com.red_escuela.backend.billing.payment.domain.Payment;
import com.red_escuela.backend.billing.payment.domain.PaymentActionAvailability;
import com.red_escuela.backend.billing.payment.domain.PaymentStatus;
import com.red_escuela.backend.billing.payment.domain.PendingRefundOfPayment;
import com.red_escuela.backend.billing.payment.domain.Refund;
import com.red_escuela.backend.billing.payment.domain.StudentPaymentOverview;
import com.red_escuela.backend.billing.payment.repository.ChargebackRepository;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class StudentPaymentOverviewService {
    private static final String AVAILABILITY_PROBE_REFERENCE = "availability-probe";
    private static final String AVAILABILITY_PROBE_REASON = "availability-probe";
    private static final PolicyViolation NO_CHARGEBACK = new PolicyViolation("CHARGEBACK_NOT_FOUND", "El pago no tiene un contracargo para revertir.");

    private final StudentPaymentQueryService paymentQueries;
    private final ChargebackRepository chargebacks;
    private final PaymentPendingRefunds pendingRefunds;
    private final StudentCurrentAccountService accountService;
    private final PaymentReversalEvaluator evaluator;
    private final Clock clock;

    public StudentPaymentOverviewService(StudentPaymentQueryService paymentQueries, ChargebackRepository chargebacks, PaymentPendingRefunds pendingRefunds,
                                         StudentCurrentAccountService accountService, PaymentReversalEvaluator evaluator, Clock clock) {
        this.paymentQueries = paymentQueries;
        this.chargebacks = chargebacks;
        this.pendingRefunds = pendingRefunds;
        this.accountService = accountService;
        this.evaluator = evaluator;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<StudentPaymentOverview> describePaymentsOfExistingStudent(Long studentId) {
        List<Payment> studentPayments = paymentQueries.findPaymentsOfExistingStudent(studentId);
        List<Long> paymentIds = studentPayments.stream().map(Payment::getId).toList();
        Map<Long, Chargeback> latestChargebackByPayment = paymentIds.isEmpty() ? Map.of() : chargebacks.findByPaymentIdInOrderByIdAsc(paymentIds).stream()
                .collect(Collectors.toMap(Chargeback::getPaymentId, Function.identity(), (older, newer) -> newer));
        Map<Long, List<Refund>> pendingByPayment = pendingRefunds.pendingRefundsByPayment(paymentIds);
        LocalDate today = LocalDate.now(clock);
        return studentPayments.stream().map(payment -> describe(payment, latestChargebackByPayment.get(payment.getId()),
                pendingByPayment.getOrDefault(payment.getId(), List.of()), today)).toList();
    }

    private StudentPaymentOverview describe(Payment payment, Chargeback latestChargeback, List<Refund> pending, LocalDate today) {
        Money pendingAmount = pending.stream().map(refund -> refund.getAmount().value()).reduce(Money.zero(payment.getCurrency()), Money::plus);
        Money credit = payment.getStatus() == PaymentStatus.APPROVED ? creditOf(payment).value() : payment.creditedAmount().value();
        PaymentActionAvailability registration = availabilityOf(evaluator.findChargebackRegistrationViolation(payment, credit, pendingAmount, credit, today,
                AVAILABILITY_PROBE_REFERENCE, today));
        PaymentActionAvailability reversal = latestChargeback == null ? PaymentActionAvailability.blockedBy(NO_CHARGEBACK)
                : availabilityOf(evaluator.findChargebackReversalViolation(latestChargeback, payment));
        PaymentActionAvailability revocation = availabilityOf(evaluator.findApprovalRevocationViolation(payment, pendingAmount, AVAILABILITY_PROBE_REASON));
        return new StudentPaymentOverview(payment, latestChargeback, pending.stream().sorted(Comparator.comparing(Refund::getId)).map(PendingRefundOfPayment::of).toList(),
                registration, reversal, revocation);
    }

    private NonNegativeMoney creditOf(Payment payment) {
        return accountService.findPaymentCreditToReverse(payment.getId()).map(entry -> entry.getAmount()).orElseGet(payment::creditedAmount);
    }

    private static PaymentActionAvailability availabilityOf(Optional<PolicyViolation> violation) {
        return violation.map(PaymentActionAvailability::blockedBy).orElseGet(PaymentActionAvailability::available);
    }

}
