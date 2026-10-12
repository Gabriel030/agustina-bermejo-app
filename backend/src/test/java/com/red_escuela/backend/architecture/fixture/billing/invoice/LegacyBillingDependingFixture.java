package com.red_escuela.backend.architecture.fixture.billing.invoice;

import com.red_escuela.backend.billing.entity.PaymentStatus;

public class LegacyBillingDependingFixture {

    public PaymentStatus legacyStatus() {
        return PaymentStatus.CONFIRMED;
    }
}
