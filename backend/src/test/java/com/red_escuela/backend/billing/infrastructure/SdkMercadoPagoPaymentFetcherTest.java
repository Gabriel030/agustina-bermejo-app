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
}
