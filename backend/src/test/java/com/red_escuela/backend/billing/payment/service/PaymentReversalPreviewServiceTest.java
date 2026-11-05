package com.red_escuela.backend.billing.payment.service;

import com.red_escuela.backend.billing.account.exception.InsufficientAccountCreditException;
import com.red_escuela.backend.billing.account.service.CurrentAccountAllocationService;
import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentEvent;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.invoice.exception.IllegalInvoicePaymentStatusTransitionException;
import com.red_escuela.backend.billing.payment.domain.Payment;
import com.red_escuela.backend.billing.payment.domain.PaymentReversalPreview;
import com.red_escuela.backend.billing.payment.domain.PaymentStatus;
import com.red_escuela.backend.billing.payment.repository.ChargebackRepository;
import com.red_escuela.backend.billing.payment.repository.PaymentRepository;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("D-206")
class PaymentReversalPreviewServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2027, 3, 9);

    private final PaymentReversalEffectsPlanner planner = mock(PaymentReversalEffectsPlanner.class);
    private final PaymentReversalEvaluator evaluator = mock(PaymentReversalEvaluator.class);
    private final Payment payment = mock(Payment.class);
    private PaymentReversalPreviewService service;

    @BeforeEach
    void wire() {
        PaymentRepository payments = mock(PaymentRepository.class);
        StudentCurrentAccountService accountService = mock(StudentCurrentAccountService.class);
        PaymentPendingRefunds pendingRefunds = mock(PaymentPendingRefunds.class);
        when(payment.getId()).thenReturn(1L);
        when(payment.getStudentCurrentAccountId()).thenReturn(2L);
        when(payment.getStatus()).thenReturn(PaymentStatus.APPROVED);
        when(payment.getCurrency()).thenReturn(Currency.ARS);
        when(payments.findById(1L)).thenReturn(Optional.of(payment));
        when(accountService.findPaymentCreditToReverse(1L)).thenReturn(Optional.empty());
        when(pendingRefunds.pendingRefundsOf(payment)).thenReturn(List.of());
        when(evaluator.findChargebackRegistrationViolation(any(), any(), any(), any(), any(), any(), any())).thenReturn(Optional.empty());
        service = new PaymentReversalPreviewService(payments, mock(ChargebackRepository.class), accountService, mock(CurrentAccountAllocationService.class), pendingRefunds,
                evaluator, planner, Clock.fixed(Instant.parse("2027-03-09T12:00:00Z"), ZoneOffset.UTC));
    }

    private PaymentReversalPreview preview() {
        return service.previewChargebackRegistration(1L, Money.zero(Currency.ARS), TODAY, "BANCO-1");
    }

    @Test
    void anInvoiceThatCannotBeReopenedBlocksThePreviewWithItsOwnCode() {
        when(planner.planPaymentCreditReversal(any(), any(), any())).thenThrow(new IllegalInvoicePaymentStatusTransitionException(InvoicePaymentStatus.PAYMENT_UNDER_REVIEW,
                InvoicePaymentEvent.PAYMENT_ALLOCATION_REVERSED_BEFORE_DUE_DATE_PASSED));

        PaymentReversalPreview preview = preview();

        assertThat(preview.allowed()).isFalse();
        assertThat(preview.blockedBy().code()).isEqualTo("INVOICE_CANNOT_BE_REOPENED");
        assertThat(preview.effects().calculated()).isFalse();
    }

    @Test
    void insufficientAccountCreditBlocksThePreviewWithItsOwnCode() {
        when(planner.planPaymentCreditReversal(any(), any(), any())).thenThrow(new InsufficientAccountCreditException());

        PaymentReversalPreview preview = preview();

        assertThat(preview.blockedBy().code()).isEqualTo("INSUFFICIENT_ACCOUNT_CREDIT");
        assertThat(preview.effects().calculated()).isFalse();
    }
}
