package com.red_escuela.backend.billing.payment.gateway;

import com.red_escuela.backend.shared.domain.Currency;

import java.util.Set;

public record PaymentGatewayCapabilities(boolean supportsOnlineCheckout, boolean supportsFullRefund, boolean supportsPartialRefund,
                                         boolean notifiesChargebacks, boolean supportsCardInstallments, Set<Currency> supportedCurrencies,
                                         boolean supportsAutomaticDebit, boolean supportsMultiItemCheckout, boolean supportsAuthorizationHold) {

    public PaymentGatewayCapabilities {
        supportedCurrencies = Set.copyOf(supportedCurrencies);
    }
}
