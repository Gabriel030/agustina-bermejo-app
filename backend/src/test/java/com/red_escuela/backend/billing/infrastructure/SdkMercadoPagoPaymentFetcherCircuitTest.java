package com.red_escuela.backend.billing.infrastructure;

import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.net.MPResponse;
import com.red_escuela.backend.billing.exception.MercadoPagoGatewayException;
import com.red_escuela.backend.billing.payment.gateway.mercadopago.MercadoPagoCircuit;
import com.red_escuela.backend.shared.exception.CircuitOpenException;
import com.red_escuela.backend.shared.resilience.CircuitBreakerFactory;
import com.red_escuela.backend.shared.resilience.CircuitBreakerState;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.springframework.mock.env.MockEnvironment;

import java.time.Clock;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

class SdkMercadoPagoPaymentFetcherCircuitTest {

    private static SdkMercadoPagoPaymentFetcher fetcherWithCircuit(CircuitBreakerFactory[] holder) {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("resilience.default.enabled", "true")
                .withProperty("resilience.default.window", "2")
                .withProperty("resilience.default.failure-threshold", "2")
                .withProperty("resilience.default.wait-open", "30s");
        CircuitBreakerFactory factory = new CircuitBreakerFactory(environment, Clock.systemUTC(), new SimpleMeterRegistry());
        holder[0] = factory;
        return new SdkMercadoPagoPaymentFetcher("token", factory);
    }

    @Test
    void theCircuitOpensAfterRepeatedProviderFailuresAndStopsCallingTheClient() {
        CircuitBreakerFactory[] holder = new CircuitBreakerFactory[1];
        SdkMercadoPagoPaymentFetcher fetcher = fetcherWithCircuit(holder);
        try (MockedConstruction<PaymentClient> constructed = Mockito.mockConstruction(PaymentClient.class, (client, context) ->
                Mockito.when(client.get(Mockito.anyLong())).thenThrow(new MPException("down")))) {
            assertThatThrownBy(() -> fetcher.fetchApproved("1")).isInstanceOf(MercadoPagoGatewayException.class);
            assertThatThrownBy(() -> fetcher.fetchApproved("1")).isInstanceOf(MercadoPagoGatewayException.class);
            assertThat(holder[0].breakerNamed(MercadoPagoCircuit.NAME).state()).isEqualTo(CircuitBreakerState.OPEN);
            assertThatThrownBy(() -> fetcher.fetchApproved("1")).isInstanceOf(CircuitOpenException.class);
            assertThat(constructed.constructed()).hasSize(2);
        }
    }

    @Test
    void a404FromMercadoPagoNeverOpensTheCircuit() {
        CircuitBreakerFactory[] holder = new CircuitBreakerFactory[1];
        SdkMercadoPagoPaymentFetcher fetcher = fetcherWithCircuit(holder);
        try (MockedConstruction<PaymentClient> ignored = Mockito.mockConstruction(PaymentClient.class, (client, context) ->
                Mockito.when(client.get(Mockito.anyLong())).thenThrow(new MPApiException("not found", new MPResponse(404, Map.of(), ""))))) {
            for (int i = 0; i < 5; i++) {
                assertThatThrownBy(() -> fetcher.fetchApproved("1")).isInstanceOf(MercadoPagoGatewayException.class);
            }
            assertThat(holder[0].breakerNamed(MercadoPagoCircuit.NAME).state()).isEqualTo(CircuitBreakerState.CLOSED);
        }
    }
}
