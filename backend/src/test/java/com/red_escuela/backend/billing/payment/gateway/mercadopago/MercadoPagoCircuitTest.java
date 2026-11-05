package com.red_escuela.backend.billing.payment.gateway.mercadopago;

import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.net.MPResponse;
import com.red_escuela.backend.billing.exception.MercadoPagoGatewayException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MercadoPagoCircuitTest {

    private static MPApiException api(int status) {
        return new MPApiException("error", new MPResponse(status, Map.of(), ""));
    }

    @Test
    void serverErrorsAndTimeoutsCountAsProviderFailures() {
        assertThat(MercadoPagoCircuit.countsAsProviderFailure(api(500))).isTrue();
        assertThat(MercadoPagoCircuit.countsAsProviderFailure(api(503))).isTrue();
        assertThat(MercadoPagoCircuit.countsAsProviderFailure(api(429))).isTrue();
        assertThat(MercadoPagoCircuit.countsAsProviderFailure(new MPException("timeout"))).isTrue();
    }

    @Test
    void clientErrorsDoNotCountAsProviderFailures() {
        assertThat(MercadoPagoCircuit.countsAsProviderFailure(api(404))).isFalse();
        assertThat(MercadoPagoCircuit.countsAsProviderFailure(api(400))).isFalse();
        assertThat(MercadoPagoCircuit.countsAsProviderFailure(new IllegalStateException("bug"))).isFalse();
    }

    @Test
    void aGatewayExceptionIsUnwrappedBeforeClassifying() {
        assertThat(MercadoPagoCircuit.countsAsProviderFailure(new MercadoPagoGatewayException("x", api(502)))).isTrue();
        assertThat(MercadoPagoCircuit.countsAsProviderFailure(new MercadoPagoGatewayException("x", new MPException("io")))).isTrue();
        assertThat(MercadoPagoCircuit.countsAsProviderFailure(new MercadoPagoGatewayException("x", api(404)))).isFalse();
    }
}
