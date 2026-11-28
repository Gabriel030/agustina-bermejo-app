package com.red_escuela.backend.architecture.fixture.pattern.r11.enrollment.service;

import com.red_escuela.backend.architecture.fixture.pattern.r11.billing.api.BillingFinder;
import com.red_escuela.backend.architecture.fixture.pattern.r11.billing.events.BillingPaidEvent;

public class CleanEnrollmentFlow {
    BillingFinder finder;
    BillingPaidEvent event;
}
