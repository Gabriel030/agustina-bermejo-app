package com.red_escuela.backend.billing.infrastructure;

import com.red_escuela.backend.shared.domain.Currency;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SdkMercadoPagoPaymentFetcherTest {

    @Test
    void anUnknownCurrencyIsMappedToEmptyInsteadOfThrowing() {
        assertEquals(Optional.empty(), SdkMercadoPagoPaymentFetcher.toCurrency("BRL"));
    }

    @Test
    void aMissingCurrencyDefaultsToArsAndUsdIsRecognized() {
        assertEquals(Optional.of(Currency.ARS), SdkMercadoPagoPaymentFetcher.toCurrency(null));
        assertEquals(Optional.of(Currency.USD), SdkMercadoPagoPaymentFetcher.toCurrency("USD"));
    }

    @Test
    void theGatewayPaymentTypeIsMappedToCreditOrDebitAndAnythingElseToNull() {
        assertEquals(com.red_escuela.backend.billing.entity.CardType.CREDIT, SdkMercadoPagoPaymentFetcher.toCardType("credit_card"));
        assertEquals(com.red_escuela.backend.billing.entity.CardType.DEBIT, SdkMercadoPagoPaymentFetcher.toCardType("debit_card"));
        assertEquals(null, SdkMercadoPagoPaymentFetcher.toCardType("account_money"));
        assertEquals(null, SdkMercadoPagoPaymentFetcher.toCardType(null));
    }
}
