package com.red_escuela.backend.billing.payment.gateway.mercadopago;

import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.net.MPHttpClient;
import com.red_escuela.backend.billing.exception.MercadoPagoGatewayException;
import com.red_escuela.backend.billing.infrastructure.SdkMercadoPagoPaymentFetcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MercadoPagoClientTimeoutsTest {

    private int connection;
    private int connectionRequest;
    private int socket;
    private MPHttpClient httpClient;
    private String accessToken;

    @BeforeEach
    void remember() {
        connection = MercadoPagoConfig.getConnectionTimeout();
        connectionRequest = MercadoPagoConfig.getConnectionRequestTimeout();
        socket = MercadoPagoConfig.getSocketTimeout();
        httpClient = MercadoPagoConfig.getHttpClient();
        accessToken = MercadoPagoConfig.getAccessToken();
    }

    @AfterEach
    void restore() {
        MercadoPagoConfig.setConnectionTimeout(connection);
        MercadoPagoConfig.setConnectionRequestTimeout(connectionRequest);
        MercadoPagoConfig.setSocketTimeout(socket);
        MercadoPagoConfig.setHttpClient(httpClient);
        MercadoPagoConfig.setAccessToken(accessToken);
    }

    @Test
    void configuredTimeoutsAreAppliedToTheSdk() {
        new MercadoPagoClientTimeouts(1111, 2222, 3333).applyToSdk();

        assertThat(MercadoPagoConfig.getConnectionTimeout()).isEqualTo(1111);
        assertThat(MercadoPagoConfig.getConnectionRequestTimeout()).isEqualTo(2222);
        assertThat(MercadoPagoConfig.getSocketTimeout()).isEqualTo(3333);
    }

    @Test
    void anSdkTimeoutBecomesAControlledGatewayErrorInsteadOfAnUnexpectedFailure() {
        MercadoPagoConfig.setHttpClient(request -> {
            throw new MPException("timeout", new SocketTimeoutException("Read timed out"));
        });

        assertThatThrownBy(() -> new SdkMercadoPagoPaymentFetcher("token").fetchApproved("123"))
                .isInstanceOf(MercadoPagoGatewayException.class);
    }
}
