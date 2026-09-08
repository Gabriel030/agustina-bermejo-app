package com.red_escuela.backend.billing.usecase.billingperiod;

import com.red_escuela.backend.billing.entity.BillingPeriod;

public interface GetCurrentBillingPeriodUseCase {
    BillingPeriod getCurrent();
}
