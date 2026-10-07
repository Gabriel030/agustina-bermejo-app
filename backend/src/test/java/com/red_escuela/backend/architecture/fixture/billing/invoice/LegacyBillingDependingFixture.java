package com.red_escuela.backend.architecture.fixture.billing.invoice;

import com.red_escuela.backend.billing.entity.InvoiceStatus;

public class LegacyBillingDependingFixture {

    public InvoiceStatus legacyStatus() {
        return InvoiceStatus.PAID;
    }
}
