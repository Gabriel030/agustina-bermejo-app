package com.red_escuela.backend.billing.infrastructure;

import com.red_escuela.backend.billing.operation.ReportedGatewayAllocation;
import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.payment.Payment;
import com.red_escuela.backend.billing.operation.CardPaymentType;
import com.red_escuela.backend.billing.payment.gateway.mercadopago.MercadoPagoCircuit;
import com.red_escuela.backend.billing.exception.MercadoPagoGatewayException;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.resilience.CircuitBreaker;
import com.red_escuela.backend.shared.resilience.CircuitBreakerFactory;
import com.red_escuela.backend.shared.resilience.CircuitBreakerSettings;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.red_escuela.backend.shared.logging.LogSanitizer;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

@Slf4j
@Component
public class SdkMercadoPagoPaymentFetcher implements MercadoPagoPaymentFetcher {
    private static final String APPROVED = "approved";

    private final String accessToken;
    private final CircuitBreaker breaker;

    @Autowired
    public SdkMercadoPagoPaymentFetcher(@Value("${mercadopago.access-token:}") String accessToken, CircuitBreakerFactory circuitBreakerFactory) {
        this.accessToken = accessToken;
        this.breaker = circuitBreakerFactory.breakerNamed(MercadoPagoCircuit.NAME);
    }

    public SdkMercadoPagoPaymentFetcher(String accessToken) {
        this.accessToken = accessToken;
        this.breaker = new CircuitBreaker(MercadoPagoCircuit.NAME, new CircuitBreakerSettings(false, 1, 1, Duration.ofSeconds(1)), Clock.systemUTC(), new SimpleMeterRegistry());
    }

    @PostConstruct
    void init() {
        MercadoPagoConfig.setAccessToken(accessToken);
    }

    @Override
    public Optional<MercadoPagoApprovedPayment> fetchApproved(String mpPaymentId) {
        Payment payment = breaker.execute(() -> {
            try {
                return new PaymentClient().get(Long.parseLong(mpPaymentId));
            } catch (MPException | MPApiException e) {
                throw new MercadoPagoGatewayException("Error al consultar pago en Mercado Pago", e);
            }
        }, MercadoPagoCircuit::countsAsProviderFailure);
        if (!APPROVED.equals(payment.getStatus())) {
            return Optional.empty();
        }
        Optional<Currency> currency = toCurrency(payment.getCurrencyId());
        if (currency.isEmpty()) {
            log.warn("Pago de Mercado Pago ignorado, moneda no soportada: pagoMp={}, moneda={}", mpPaymentId, payment.getCurrencyId());
            return Optional.empty();
        }
        Optional<Long> invoiceId = parseInvoiceReference(payment.getExternalReference());
        if (invoiceId.isEmpty()) {
            log.warn("Pago de Mercado Pago sin referencia externa válida: pagoMp={}", LogSanitizer.sanitize(mpPaymentId));
            return Optional.empty();
        }
        ReportedGatewayAllocation reported = new ReportedGatewayAllocation(
                invoiceId.get(),
                Money.of(payment.getTransactionAmount(), currency.get()));
        return Optional.of(new MercadoPagoApprovedPayment(reported, payment.getInstallments(), toCardType(payment.getPaymentTypeId())));
    }

    static Optional<Long> parseInvoiceReference(String externalReference) {
        if (externalReference == null || !externalReference.matches("\\d{1,18}")) {
            return Optional.empty();
        }
        return Optional.of(Long.parseLong(externalReference));
    }

    static CardPaymentType toCardType(String paymentTypeId) {
        if ("credit_card".equals(paymentTypeId)) {
            return CardPaymentType.CREDIT;
        }
        if ("debit_card".equals(paymentTypeId)) {
            return CardPaymentType.DEBIT;
        }
        return null;
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
