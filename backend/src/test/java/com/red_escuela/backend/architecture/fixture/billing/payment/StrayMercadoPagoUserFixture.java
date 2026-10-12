package com.red_escuela.backend.architecture.fixture.billing.payment;

import com.mercadopago.client.payment.PaymentClient;

public class StrayMercadoPagoUserFixture {

    public PaymentClient createClientOutsideTheAdapter() {
        return new PaymentClient();
    }
}
