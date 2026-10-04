package com.red_escuela.backend.billing.infrastructure;

import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.payment.Payment;
import com.red_escuela.backend.billing.dto.PaymentAllocationRequest;
import com.red_escuela.backend.billing.exception.MercadoPagoGatewayException;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
public class SdkMercadoPagoPaymentFetcher implements MercadoPagoPaymentFetcher {
    private static final String APPROVED = "approved";

    private final String accessToken;

    public SdkMercadoPagoPaymentFetcher(@Value("${mercadopago.access-token:}") String accessToken) {
        this.accessToken = accessToken;
    }

    @PostConstruct
    void init() {
        MercadoPagoConfig.setAccessToken(accessToken);
    }

    @Override
    public Optional<MercadoPagoApprovedPayment> fetchApproved(String mpPaymentId) {
        Payment payment;
        try {
            payment = new PaymentClient().get(Long.parseLong(mpPaymentId));
        } catch (MPException | MPApiException e) {
            throw new MercadoPagoGatewayException("Error al consultar pago en Mercado Pago", e);
        }
        if (!APPROVED.equals(payment.getStatus())) {
            return Optional.empty();
        }
        Optional<Currency> currency = toCurrency(payment.getCurrencyId());
        if (currency.isEmpty()) {
            log.warn("Pago de Mercado Pago ignorado, moneda no soportada: pagoMp={}, moneda={}", mpPaymentId, payment.getCurrencyId());
            return Optional.empty();
        }
        PaymentAllocationRequest reported = new PaymentAllocationRequest(
                Long.parseLong(payment.getExternalReference()),
                Money.of(payment.getTransactionAmount(), currency.get()));
        return Optional.of(new MercadoPagoApprovedPayment(reported, payment.getInstallments()));
    }

    static Optional<Currency> toCurrency(String currencyId) {
        if (currencyId == null) {
            return Optional.of(Currency.ARS);
        }
        try {
            return Optional.of(Currency.valueOf(currencyId));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
