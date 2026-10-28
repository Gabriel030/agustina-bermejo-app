package com.red_escuela.backend.billing.payment.service;

import com.red_escuela.backend.billing.account.service.CurrentAccountAllocationService;
import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.payment.domain.Chargeback;
import com.red_escuela.backend.billing.account.exception.InsufficientAccountCreditException;
import com.red_escuela.backend.billing.invoice.exception.IllegalInvoicePaymentStatusTransitionException;
import com.red_escuela.backend.billing.payment.domain.Payment;
import com.red_escuela.backend.billing.payment.domain.PaymentReversalEffects;
import com.red_escuela.backend.billing.payment.domain.PaymentReversalPreview;
import com.red_escuela.backend.billing.payment.domain.PendingRefundOfPayment;
import com.red_escuela.backend.billing.payment.domain.Refund;
import com.red_escuela.backend.billing.payment.exception.ChargebackNotFoundException;
import com.red_escuela.backend.billing.payment.exception.PaymentNotFoundException;
import com.red_escuela.backend.billing.payment.repository.ChargebackRepository;
import com.red_escuela.backend.billing.payment.repository.PaymentRepository;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
public class PaymentReversalPreviewService {
    private static final PolicyViolation INVOICE_CANNOT_BE_REOPENED = new PolicyViolation("INVOICE_CANNOT_BE_REOPENED",
            "Una factura imputada con este pago no está en un estado que admita reabrirla.");
    private static final PolicyViolation INSUFFICIENT_ACCOUNT_CREDIT = new PolicyViolation("INSUFFICIENT_ACCOUNT_CREDIT",
            "La cuenta no tiene crédito suficiente para revertir este pago.");

    private final PaymentRepository payments;
    private final ChargebackRepository chargebacks;
    private final StudentCurrentAccountService accountService;
    private final CurrentAccountAllocationService allocationService;
    private final PaymentPendingRefunds pendingRefunds;
    private final PaymentReversalEvaluator evaluator;
    private final PaymentReversalEffectsPlanner effectsPlanner;
    private final Clock clock;

    public PaymentReversalPreviewService(PaymentRepository payments, ChargebackRepository chargebacks, StudentCurrentAccountService accountService,
                                         CurrentAccountAllocationService allocationService, PaymentPendingRefunds pendingRefunds, PaymentReversalEvaluator evaluator,
                                         PaymentReversalEffectsPlanner effectsPlanner, Clock clock) {
        this.payments = payments;
        this.chargebacks = chargebacks;
        this.accountService = accountService;
        this.allocationService = allocationService;
        this.pendingRefunds = pendingRefunds;
        this.evaluator = evaluator;
        this.effectsPlanner = effectsPlanner;
        this.clock = clock;
    }

    public PaymentReversalPreview previewChargebackRegistration(Long paymentId, Money amount, LocalDate chargedBackOn, String reference) {
        Payment payment = payments.findById(paymentId).orElseThrow(PaymentNotFoundException::new);
        NonNegativeMoney credit = creditToReverseOf(payment);
        List<Refund> pending = pendingRefunds.pendingRefundsOf(payment);
        PolicyViolation violation = evaluator.findChargebackRegistrationViolation(payment, credit.value(), sumOf(pending, payment), amount, chargedBackOn, reference,
                LocalDate.now(clock)).orElse(null);
        return previewOf(payment, violation, credit, pending);
    }

    public PaymentReversalPreview previewApprovalRevocation(Long paymentId, String reason) {
        Payment payment = payments.findById(paymentId).orElseThrow(PaymentNotFoundException::new);
        NonNegativeMoney credit = creditToReverseOf(payment);
        List<Refund> pending = pendingRefunds.pendingRefundsOf(payment);
        PolicyViolation violation = evaluator.findApprovalRevocationViolation(payment, sumOf(pending, payment), reason).orElse(null);
        return previewOf(payment, violation, credit, pending);
    }

    public PaymentReversalPreview previewChargebackReversal(Long chargebackId) {
        Chargeback chargeback = chargebacks.findById(chargebackId).orElseThrow(ChargebackNotFoundException::new);
        Payment payment = payments.findById(chargeback.getPaymentId()).orElseThrow(PaymentNotFoundException::new);
        PolicyViolation violation = evaluator.findChargebackReversalViolation(chargeback, payment).orElse(null);
        PaymentReversalEffects effects = violation == null ? effectsPlanner.planChargebackReversal(payment, chargeback) : PaymentReversalEffects.notCalculated();
        return new PaymentReversalPreview(payment.getId(), payment.getStatus(), violation, chargeback.getAmount(), List.of(), List.of(), effects);
    }

    private PaymentReversalPreview previewOf(Payment payment, PolicyViolation violation, NonNegativeMoney credit, List<Refund> pending) {
        PolicyViolation blockedBy = violation;
        PaymentReversalEffects effects = PaymentReversalEffects.notCalculated();
        if (violation == null) {
            try {
                effects = effectsPlanner.planPaymentCreditReversal(payment, credit, LocalDate.now(clock));
            } catch (InsufficientAccountCreditException insufficient) {
                blockedBy = INSUFFICIENT_ACCOUNT_CREDIT;
            } catch (IllegalInvoicePaymentStatusTransitionException cannotReopen) {
                blockedBy = INVOICE_CANNOT_BE_REOPENED;
            }
        }
        return new PaymentReversalPreview(payment.getId(), payment.getStatus(), blockedBy, credit, pending.stream().map(PendingRefundOfPayment::of).toList(),
                allocationService.listInvoiceIdsFundedByPayment(payment.getStudentCurrentAccountId(), payment.getId()), effects);
    }

    private NonNegativeMoney creditToReverseOf(Payment payment) {
        return accountService.findPaymentCreditToReverse(payment.getId()).map(entry -> entry.getAmount()).orElseGet(() -> NonNegativeMoney.zero(payment.getCurrency()));
    }

    private static Money sumOf(List<Refund> pending, Payment payment) {
        return pending.stream().map(refund -> refund.getAmount().value()).reduce(Money.zero(payment.getCurrency()), Money::plus);
    }
}
