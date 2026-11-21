package com.red_escuela.backend.billing.payment.gateway.mercadopago;

import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.preference.Preference;
import com.red_escuela.backend.billing.exception.MercadoPagoGatewayException;
import com.red_escuela.backend.billing.operation.CreatedMercadoPagoPreference;
import com.red_escuela.backend.billing.operation.NewMercadoPagoPreference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SdkMercadoPagoPreferenceGatewayTest {

    private final SdkMercadoPagoPreferenceGateway gateway = new SdkMercadoPagoPreferenceGateway("token");

    private NewMercadoPagoPreference newPreference() {
        return new NewMercadoPagoPreference("Cuota 2026-10", new BigDecimal("1500.50"), UUID.randomUUID(),
                "http://front/ok", "http://front/ko", "http://front/wait", "http://back/hook");
    }

    @Test
    void sdkPreferenceIsBuiltFromTheGatewayRequest() throws Exception {
        NewMercadoPagoPreference preference = newPreference();
        Preference created = Mockito.mock(Preference.class);
        Mockito.when(created.getId()).thenReturn("pref-9");
        Mockito.when(created.getInitPoint()).thenReturn("https://mp/init/pref-9");
        try (MockedConstruction<PreferenceClient> construction = Mockito.mockConstruction(PreferenceClient.class,
                (client, context) -> Mockito.when(client.create(Mockito.any(PreferenceRequest.class))).thenReturn(created))) {
            CreatedMercadoPagoPreference result = gateway.createPreference(preference);

            ArgumentCaptor<PreferenceRequest> captor = ArgumentCaptor.forClass(PreferenceRequest.class);
            Mockito.verify(construction.constructed().get(0)).create(captor.capture());
            PreferenceRequest request = captor.getValue();
            assertThat(request.getExternalReference()).isEqualTo(preference.publicReference().toString());
            assertThat(request.getItems().get(0).getUnitPrice()).isEqualByComparingTo("1500.50");
            assertThat(request.getItems().get(0).getCurrencyId()).isEqualTo("ARS");
            assertThat(request.getBackUrls().getSuccess()).isEqualTo("http://front/ok");
            assertThat(request.getNotificationUrl()).isEqualTo("http://back/hook");
            assertThat(result).isEqualTo(new CreatedMercadoPagoPreference("pref-9", "https://mp/init/pref-9"));
        }
    }

    @Test
    void gatewayErrorsAreTranslatedToDomainException() {
        MPException sdkFailure = new MPException("down");
        try (MockedConstruction<PreferenceClient> ignored = Mockito.mockConstruction(PreferenceClient.class,
                (client, context) -> Mockito.when(client.create(Mockito.any(PreferenceRequest.class))).thenThrow(sdkFailure))) {
            assertThatThrownBy(() -> gateway.createPreference(newPreference()))
                    .isInstanceOf(MercadoPagoGatewayException.class)
                    .hasCause(sdkFailure);
        }
    }
}
