package com.red_escuela.backend.billing.payment.service;

import com.red_escuela.backend.billing.infrastructure.MercadoPagoApprovedPayment;
import com.red_escuela.backend.billing.infrastructure.MercadoPagoPaymentFetcher;
import com.red_escuela.backend.billing.infrastructure.MpWebhookSignatureVerifier;
import com.red_escuela.backend.billing.operation.ApprovedPaymentRegistrationPort;
import com.red_escuela.backend.billing.operation.MercadoPagoCheckoutMatch;
import com.red_escuela.backend.billing.operation.ReportedGatewayAllocation;
import com.red_escuela.backend.billing.payment.domain.GatewayCheckout;
import com.red_escuela.backend.billing.payment.domain.PaymentGatewayCode;
import com.red_escuela.backend.billing.payment.repository.GatewayCheckoutRepository;
import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import com.red_escuela.backend.shared.audit.AuditRecord;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("D-537")
@Tag("D-538")
class MercadoPagoCheckoutResolutionTest {

    private static final UUID REFERENCE = UUID.randomUUID();
    private static final String MP_PAYMENT_ID = "mp-1";
    private static final Money PAID = Money.of(new BigDecimal("1200.00"), Currency.ARS);

    private final ApprovedPaymentRegistrationPort port = Mockito.mock(ApprovedPaymentRegistrationPort.class);
    private final MercadoPagoPaymentFetcher fetcher = Mockito.mock(MercadoPagoPaymentFetcher.class);
    private final MpWebhookSignatureVerifier verifier = Mockito.mock(MpWebhookSignatureVerifier.class);
    private final MercadoPagoWebhookRegistration registration = new MercadoPagoWebhookRegistration(port, fetcher, verifier);
    private final GatewayCheckoutRepository checkouts = Mockito.mock(GatewayCheckoutRepository.class);
    private final AuditTrail auditTrail = Mockito.mock(AuditTrail.class);
    private final ApprovedPaymentRegistrationAdapter adapter =
            new ApprovedPaymentRegistrationAdapter(null, null, null, null, null, checkouts, auditTrail);

    private void approvedPaymentWithReference() {
        when(fetcher.fetchApproved(MP_PAYMENT_ID)).thenReturn(Optional.of(new MercadoPagoApprovedPayment(new ReportedGatewayAllocation(REFERENCE, PAID), 1)));
    }

    private GatewayCheckout checkout(PaymentGatewayCode gateway, LocalDateTime expiresAt) {
        return GatewayCheckout.builder().publicReference(REFERENCE).invoiceId(77L).gateway(gateway)
                .amount(NonNegativeMoney.of(new BigDecimal("1500.00"), Currency.ARS)).gatewayPreferenceId("pref")
                .createdAt(expiresAt.minusHours(48)).expiresAt(expiresAt).build();
    }

    @Test
    void paymentWithUnknownReferenceIsNotCredited() {
        approvedPaymentWithReference();
        when(port.findMercadoPagoCheckout(REFERENCE)).thenReturn(Optional.empty());

        registration.register("", "request", MP_PAYMENT_ID);

        verify(port, never()).registerApprovedMercadoPagoPayment(any(), any(), any(), any(), anyString());
    }

    @Test
    void paymentIsCreditedToTheCheckoutInvoice() {
        approvedPaymentWithReference();
        when(port.findMercadoPagoCheckout(REFERENCE)).thenReturn(Optional.of(
                new MercadoPagoCheckoutMatch(77L, Money.of(new BigDecimal("1500.00"), Currency.ARS), false, null)));

        registration.register("", "request", MP_PAYMENT_ID);

        verify(port).registerApprovedMercadoPagoPayment(eq(77L), eq(PAID), eq(1), eq(null), eq(MP_PAYMENT_ID));
    }

    @Test
    void replayedWebhookStaysIdempotent() {
        approvedPaymentWithReference();
        when(port.isMercadoPagoPaymentRegistered(MP_PAYMENT_ID)).thenReturn(false, true);
        when(port.findMercadoPagoCheckout(REFERENCE)).thenReturn(Optional.of(
                new MercadoPagoCheckoutMatch(77L, PAID, false, null)));

        registration.register("", "request-1", MP_PAYMENT_ID);
        registration.register("", "request-2", MP_PAYMENT_ID);

        verify(port, times(1)).registerApprovedMercadoPagoPayment(any(), any(), any(), any(), anyString());
    }

    @Test
    void checkoutOfAnotherGatewayIsNotMatched() {
        when(checkouts.findByPublicReference(REFERENCE)).thenReturn(Optional.of(
                checkout(PaymentGatewayCode.DLOCAL, ApplicationTime.now().plusHours(1))));

        assertThat(adapter.findMercadoPagoCheckout(REFERENCE)).isEmpty();
    }

    @Test
    void checkoutPastItsExpiryIsFlaggedExpiredButStillMatched() {
        when(checkouts.findByPublicReference(REFERENCE)).thenReturn(Optional.of(
                checkout(PaymentGatewayCode.MERCADO_PAGO, ApplicationTime.now().minusHours(1))));

        MercadoPagoCheckoutMatch match = adapter.findMercadoPagoCheckout(REFERENCE).orElseThrow();

        assertThat(match.expired()).isTrue();
        assertThat(match.invoiceId()).isEqualTo(77L);
        assertThat(match.expectedAmount()).isEqualTo(Money.of(new BigDecimal("1500.00"), Currency.ARS));
    }

    @Test
    void liveCheckoutIsNotFlaggedExpired() {
        when(checkouts.findByPublicReference(REFERENCE)).thenReturn(Optional.of(
                checkout(PaymentGatewayCode.MERCADO_PAGO, ApplicationTime.now().plusHours(1))));

        assertThat(adapter.findMercadoPagoCheckout(REFERENCE).orElseThrow().expired()).isFalse();
    }

    @Test
    void amountMismatchIsCreditedAndAudited() {
        approvedPaymentWithReference();
        Money expected = Money.of(new BigDecimal("1500.00"), Currency.ARS);
        when(port.findMercadoPagoCheckout(REFERENCE)).thenReturn(Optional.of(new MercadoPagoCheckoutMatch(77L, expected, false, null)));

        registration.register("", "request", MP_PAYMENT_ID);

        verify(port).registerApprovedMercadoPagoPayment(eq(77L), eq(PAID), eq(1), eq(null), eq(MP_PAYMENT_ID));
        verify(port).recordMercadoPagoAmountMismatch(77L, expected, PAID, MP_PAYMENT_ID);
        verify(port, never()).recordMercadoPagoExpiredCheckout(any(), anyString());
    }

    @Test
    void matchingAmountOnALiveCheckoutIsNotAudited() {
        approvedPaymentWithReference();
        when(port.findMercadoPagoCheckout(REFERENCE)).thenReturn(Optional.of(new MercadoPagoCheckoutMatch(77L, PAID, false, null)));

        registration.register("", "request", MP_PAYMENT_ID);

        verify(port, never()).recordMercadoPagoAmountMismatch(any(), any(), any(), anyString());
        verify(port, never()).recordMercadoPagoExpiredCheckout(any(), anyString());
    }

    @Test
    void expiredCheckoutIsCreditedAndAudited() {
        approvedPaymentWithReference();
        when(port.findMercadoPagoCheckout(REFERENCE)).thenReturn(Optional.of(new MercadoPagoCheckoutMatch(77L, PAID, true, null)));

        registration.register("", "request", MP_PAYMENT_ID);

        verify(port).registerApprovedMercadoPagoPayment(eq(77L), eq(PAID), eq(1), eq(null), eq(MP_PAYMENT_ID));
        verify(port).recordMercadoPagoExpiredCheckout(77L, MP_PAYMENT_ID);
    }

    @Test
    void unmatchedPaymentIsAuditedAndNotCredited() {
        approvedPaymentWithReference();
        when(port.findMercadoPagoCheckout(REFERENCE)).thenReturn(Optional.empty());

        registration.register("", "request", MP_PAYMENT_ID);

        verify(port).recordMercadoPagoUnmatchedPayment(MP_PAYMENT_ID, REFERENCE);
        verify(port, never()).registerApprovedMercadoPagoPayment(any(), any(), any(), any(), anyString());
    }

    @Test
    void auditRowsCarryTheInvoiceAndBothAmounts() {
        adapter.recordMercadoPagoAmountMismatch(77L, Money.of(new BigDecimal("1500.00"), Currency.ARS), PAID, "123");

        ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(auditTrail).record(captor.capture());
        AuditRecord record = captor.getValue();
        assertThat(record.action()).isEqualTo(AuditAction.GATEWAY_PAYMENT_AMOUNT_MISMATCH);
        assertThat(record.entityType()).isEqualTo(AuditEntityType.INVOICE);
        assertThat(record.entityId()).isEqualTo(77L);
        assertThat(record.beforeValue()).isEqualTo("1500.00 ARS");
        assertThat(record.afterValue()).isEqualTo("1200.00 ARS");
    }

    @Test
    void unmatchedPaymentWithANonNumericGatewayIdWritesNoAuditRow() {
        adapter.recordMercadoPagoUnmatchedPayment("mp-abc", REFERENCE);

        Mockito.verifyNoInteractions(auditTrail);
    }

    @Test
    void currencyMismatchIsNotCreditedButAudited() {
        when(fetcher.fetchApproved(MP_PAYMENT_ID)).thenReturn(Optional.of(new MercadoPagoApprovedPayment(
                new ReportedGatewayAllocation(REFERENCE, Money.of(new BigDecimal("1500.00"), Currency.USD)), 1)));
        Money expected = Money.of(new BigDecimal("1500.00"), Currency.ARS);
        when(port.findMercadoPagoCheckout(REFERENCE)).thenReturn(Optional.of(new MercadoPagoCheckoutMatch(77L, expected, false, null)));

        registration.register("", "request", MP_PAYMENT_ID);

        verify(port, never()).registerApprovedMercadoPagoPayment(any(), any(), any(), any(), anyString());
        verify(port).recordMercadoPagoAmountMismatch(77L, expected, Money.of(new BigDecimal("1500.00"), Currency.USD), MP_PAYMENT_ID);
    }

    @Test
    void firstPaymentMarksTheCheckoutAsCredited() {
        approvedPaymentWithReference();
        when(port.findMercadoPagoCheckout(REFERENCE)).thenReturn(Optional.of(new MercadoPagoCheckoutMatch(77L, PAID, false, null)));

        registration.register("", "request", MP_PAYMENT_ID);

        verify(port).markMercadoPagoCheckoutCredited(REFERENCE, MP_PAYMENT_ID);
        verify(port, never()).recordMercadoPagoDuplicatePaymentForCheckout(any(), anyString(), anyString());
    }

    @Test
    void secondPaymentOnACreditedCheckoutIsCreditedWithAnAuditRow() {
        approvedPaymentWithReference();
        when(port.findMercadoPagoCheckout(REFERENCE)).thenReturn(Optional.of(new MercadoPagoCheckoutMatch(77L, PAID, false, "mp-0")));

        registration.register("", "request", MP_PAYMENT_ID);

        verify(port).registerApprovedMercadoPagoPayment(eq(77L), eq(PAID), eq(1), eq(null), eq(MP_PAYMENT_ID));
        verify(port).recordMercadoPagoDuplicatePaymentForCheckout(77L, "mp-0", MP_PAYMENT_ID);
        verify(port, never()).markMercadoPagoCheckoutCredited(any(), anyString());
    }
}
