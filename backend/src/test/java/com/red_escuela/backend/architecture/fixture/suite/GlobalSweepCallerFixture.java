package com.red_escuela.backend.architecture.fixture.suite;

import com.red_escuela.backend.billing.service.InvoiceOverdueSweepService;

import java.time.LocalDate;

public class GlobalSweepCallerFixture {

    public void sweep(InvoiceOverdueSweepService service) {
        service.run(LocalDate.now());
    }
}
