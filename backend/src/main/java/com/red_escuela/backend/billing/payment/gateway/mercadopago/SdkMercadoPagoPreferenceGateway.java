package com.red_escuela.backend.billing.payment.gateway.mercadopago;

import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.preference.PreferenceBackUrlsRequest;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.preference.Preference;
import com.red_escuela.backend.billing.exception.MercadoPagoGatewayException;
import com.red_escuela.backend.billing.operation.CreatedMercadoPagoPreference;
import com.red_escuela.backend.billing.operation.MercadoPagoPreferenceGateway;
import com.red_escuela.backend.billing.operation.NewMercadoPagoPreference;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class SdkMercadoPagoPreferenceGateway implements MercadoPagoPreferenceGateway {
    private final String accessToken;

    public SdkMercadoPagoPreferenceGateway(@Value("${mercadopago.access-token:}") String accessToken) {
        this.accessToken = accessToken;
    }

    @PostConstruct
    void init() {
        MercadoPagoConfig.setAccessToken(accessToken);
    }

    @Override
    public CreatedMercadoPagoPreference createPreference(NewMercadoPagoPreference preference) {
        PreferenceRequest request = PreferenceRequest.builder()
                .items(List.of(PreferenceItemRequest.builder()
                        .title(preference.title())
                        .quantity(1)
                        .unitPrice(preference.amount())
                        .currencyId("ARS")
                        .build()))
                .externalReference(preference.publicReference().toString())
                .backUrls(PreferenceBackUrlsRequest.builder()
                        .success(preference.successUrl())
                        .failure(preference.failureUrl())
                        .pending(preference.pendingUrl())
                        .build())
                .notificationUrl(preference.notificationUrl())
                .build();
        try {
            Preference created = new PreferenceClient().create(request);
            return new CreatedMercadoPagoPreference(created.getId(), created.getInitPoint());
        } catch (MPException | MPApiException | RuntimeException e) {
            log.error("Falló la creación de la preferencia de Mercado Pago: referencia={}", preference.publicReference(), e);
            throw new MercadoPagoGatewayException("Error al crear preferencia de pago", e);
        }
    }
}
