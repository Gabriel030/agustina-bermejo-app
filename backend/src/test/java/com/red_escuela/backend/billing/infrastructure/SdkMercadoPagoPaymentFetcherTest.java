package com.red_escuela.backend.billing.infrastructure;

import com.red_escuela.backend.shared.domain.Currency;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.payment.Payment;
import com.red_escuela.backend.billing.operation.CardPaymentType;
import com.red_escuela.backend.billing.exception.MercadoPagoGatewayException;
import com.red_escuela.backend.shared.domain.Money;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SdkMercadoPagoPaymentFetcherTest {

    private String previousAccessToken;

    @BeforeEach
    void rememberAccessToken() {
        previousAccessToken = MercadoPagoConfig.getAccessToken();
    }

    @AfterEach
    void restoreAccessToken() {
        MercadoPagoConfig.setAccessToken(previousAccessToken);
    }

    @Test
    void anUnknownCurrencyIsMappedToEmptyInsteadOfThrowing() {
        assertEquals(Optional.empty(), SdkMercadoPagoPaymentFetcher.toCurrency("BRL"));
    }

    @Test
    @Tag("contradicts-D-098")
    void aMissingCurrencyDefaultsToArsAndUsdIsRecognized() {
        assertEquals(Optional.of(Currency.ARS), SdkMercadoPagoPaymentFetcher.toCurrency(null));
        assertEquals(Optional.of(Currency.USD), SdkMercadoPagoPaymentFetcher.toCurrency("USD"));
    }

    @Test
    @Tag("D-079")
    void theGatewayPaymentTypeIsMappedToCreditOrDebitAndAnythingElseToNull() {
        assertEquals(CardPaymentType.CREDIT, SdkMercadoPagoPaymentFetcher.toCardType("credit_card"));
        assertEquals(CardPaymentType.DEBIT, SdkMercadoPagoPaymentFetcher.toCardType("debit_card"));
        assertEquals(null, SdkMercadoPagoPaymentFetcher.toCardType("account_money"));
        assertEquals(null, SdkMercadoPagoPaymentFetcher.toCardType(null));
    }

    private static Payment payment(String status, String currency, String type, String externalReference, String amount, Integer installments) {
        Payment payment = Mockito.mock(Payment.class);
        Mockito.when(payment.getStatus()).thenReturn(status);
        Mockito.when(payment.getCurrencyId()).thenReturn(currency);
        Mockito.when(payment.getPaymentTypeId()).thenReturn(type);
        Mockito.when(payment.getExternalReference()).thenReturn(externalReference);
        Mockito.when(payment.getTransactionAmount()).thenReturn(new BigDecimal(amount));
        Mockito.when(payment.getInstallments()).thenReturn(installments);
        return payment;
    }

    private static Optional<MercadoPagoApprovedPayment> fetchWith(Payment payment, String mpPaymentId) {
        try (MockedConstruction<PaymentClient> ignored = Mockito.mockConstruction(PaymentClient.class, (client, context) -> {
            Mockito.when(client.get(Mockito.anyLong())).thenReturn(payment);
        })) {
            return new SdkMercadoPagoPaymentFetcher("token").fetchApproved(mpPaymentId);
        }
    }

    @Test
    void anApprovedCreditCardPaymentIsMappedWithTheReportedInvoiceAmountInstallmentsAndCardType() {
        Optional<MercadoPagoApprovedPayment> fetched = fetchWith(payment("approved", "ARS", "credit_card", "45", "1500.50", 3), "999");

        assertThat(fetched).isPresent();
        assertThat(fetched.get().reportedAllocation().invoiceId()).isEqualTo(45L);
        assertThat(fetched.get().reportedAllocation().amountApplied()).isEqualTo(Money.of(new BigDecimal("1500.50"), Currency.ARS));
        assertThat(fetched.get().installments()).isEqualTo(3);
        assertThat(fetched.get().cardType()).isEqualTo(CardPaymentType.CREDIT);
    }

    @Test
    void thePaymentIsRequestedByItsNumericGatewayId() throws Exception {
        Payment approved = payment("approved", "USD", "debit_card", "7", "10.00", 1);
        try (MockedConstruction<PaymentClient> construction = Mockito.mockConstruction(PaymentClient.class, (client, context) -> {
            Mockito.when(client.get(Mockito.anyLong())).thenReturn(approved);
        })) {
            Optional<MercadoPagoApprovedPayment> fetched = new SdkMercadoPagoPaymentFetcher("token").fetchApproved("1234567");

            Mockito.verify(construction.constructed().get(0)).get(1234567L);
            assertThat(fetched.orElseThrow().reportedAllocation().amountApplied().currency()).isEqualTo(Currency.USD);
            assertThat(fetched.get().cardType()).isEqualTo(CardPaymentType.DEBIT);
        }
    }

    @Test
    void aPaymentThatIsNotApprovedIsMappedToEmpty() {
        assertThat(fetchWith(payment("pending", "ARS", "credit_card", "45", "10.00", 1), "999")).isEmpty();
        assertThat(fetchWith(payment(null, "ARS", "credit_card", "45", "10.00", 1), "999")).isEmpty();
        assertThat(fetchWith(payment("rejected", "ARS", "credit_card", "45", "10.00", 1), "999")).isEmpty();
    }

    @Test
    void anApprovedPaymentInAnUnsupportedCurrencyIsIgnored() {
        assertThat(fetchWith(payment("approved", "BRL", "credit_card", "45", "10.00", 1), "999")).isEmpty();
    }

    @Test
    void anApprovedPaymentWithoutCurrencyIsTakenAsArs() {
        Optional<MercadoPagoApprovedPayment> fetched = fetchWith(payment("approved", null, "account_money", "45", "10.00", 1), "999");

        assertThat(fetched.orElseThrow().reportedAllocation().amountApplied().currency()).isEqualTo(Currency.ARS);
        assertThat(fetched.get().cardType()).isNull();
    }

    @Test
    void aGatewayFailureIsWrappedInTheDomainException() {
        try (MockedConstruction<PaymentClient> ignored = Mockito.mockConstruction(PaymentClient.class, (client, context) -> {
            Mockito.when(client.get(Mockito.anyLong())).thenThrow(new MPException("down"));
        })) {
            assertThatThrownBy(() -> new SdkMercadoPagoPaymentFetcher("token").fetchApproved("999")).isInstanceOf(MercadoPagoGatewayException.class)
                    .hasMessage("Error al consultar pago en Mercado Pago").hasCauseInstanceOf(MPException.class);
        }
    }

    @Test
    void initConfiguresTheSdkWithTheAccessToken() {
        MercadoPagoConfig.setAccessToken("stale");

        new SdkMercadoPagoPaymentFetcher("test-token-xyz").init();

        assertThat(MercadoPagoConfig.getAccessToken()).isEqualTo("test-token-xyz");
    }
}
