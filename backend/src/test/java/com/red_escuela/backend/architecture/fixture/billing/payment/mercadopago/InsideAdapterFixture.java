package com.red_escuela.backend.architecture.fixture.billing.payment.mercadopago;

import com.mercadopago.client.payment.PaymentClient;

public class InsideAdapterFixture {

    public PaymentClient createClientInsideTheAdapter() {
        return new PaymentClient();
    }
}
