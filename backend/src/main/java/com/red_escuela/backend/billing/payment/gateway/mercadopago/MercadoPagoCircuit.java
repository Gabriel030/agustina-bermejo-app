package com.red_escuela.backend.billing.payment.gateway.mercadopago;

import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.red_escuela.backend.billing.exception.MercadoPagoGatewayException;

public final class MercadoPagoCircuit {
    public static final String NAME = "mercadopago";
    private static final int TOO_MANY_REQUESTS = 429;
    private static final int SERVER_ERROR_FLOOR = 500;

    private MercadoPagoCircuit() {
    }

    public static boolean countsAsProviderFailure(Throwable failure) {
        Throwable cause = failure instanceof MercadoPagoGatewayException ? failure.getCause() : failure;
        if (cause instanceof MPApiException api) {
            int status = api.getStatusCode();
            return status >= SERVER_ERROR_FLOOR || status == TOO_MANY_REQUESTS;
        }
        return cause instanceof MPException;
    }
}
