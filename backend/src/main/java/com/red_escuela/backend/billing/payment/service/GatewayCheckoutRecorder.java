package com.red_escuela.backend.billing.payment.service;

import com.red_escuela.backend.billing.operation.GatewayCheckoutRecording;
import com.red_escuela.backend.billing.operation.NewGatewayCheckout;
import com.red_escuela.backend.billing.payment.domain.GatewayCheckout;
import com.red_escuela.backend.billing.payment.domain.PaymentGatewayCode;
import com.red_escuela.backend.billing.payment.repository.GatewayCheckoutRepository;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class GatewayCheckoutRecorder implements GatewayCheckoutRecording {

    private final GatewayCheckoutRepository gatewayCheckoutRepository;
    private final Duration checkoutExpiration;

    public GatewayCheckoutRecorder(GatewayCheckoutRepository gatewayCheckoutRepository,
                                   @Value("${mercadopago.checkout-expiration-hours:48}") long checkoutExpirationHours) {
        this.gatewayCheckoutRepository = gatewayCheckoutRepository;
        this.checkoutExpiration = Duration.ofHours(checkoutExpirationHours);
    }

    @Override
    public void recordMercadoPagoCheckout(NewGatewayCheckout checkout) {
        LocalDateTime createdAt = ApplicationTime.now();
        gatewayCheckoutRepository.save(GatewayCheckout.builder()
                .publicReference(checkout.publicReference())
                .invoiceId(checkout.invoiceId())
                .gateway(PaymentGatewayCode.MERCADO_PAGO)
                .amount(checkout.amount())
                .gatewayPreferenceId(checkout.gatewayPreferenceId())
                .createdAt(createdAt)
                .expiresAt(createdAt.plus(checkoutExpiration))
                .build());
    }
}
