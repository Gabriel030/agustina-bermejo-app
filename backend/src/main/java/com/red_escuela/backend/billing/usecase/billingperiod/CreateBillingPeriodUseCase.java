package com.red_escuela.backend.billing.usecase.billingperiod;

import com.red_escuela.backend.billing.dto.CreateBillingPeriodRequest;
import com.red_escuela.backend.billing.entity.BillingPeriod;

public interface CreateBillingPeriodUseCase {
    BillingPeriod create(CreateBillingPeriodRequest request);
}
