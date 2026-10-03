package com.red_escuela.backend.billing.infrastructure;

import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.payment.Payment;
import com.red_escuela.backend.billing.dto.PaymentAllocationRequest;
import com.red_escuela.backend.billing.exception.MercadoPagoGatewayException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

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
        PaymentAllocationRequest reported = new PaymentAllocationRequest(
                Long.parseLong(payment.getExternalReference()), payment.getTransactionAmount());
        return Optional.of(new MercadoPagoApprovedPayment(reported, payment.getInstallments()));
    }
}
