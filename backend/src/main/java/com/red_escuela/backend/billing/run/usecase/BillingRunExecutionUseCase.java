package com.red_escuela.backend.billing.run.usecase;

import com.red_escuela.backend.billing.run.dto.NightlyBillingExecutionResponse;

public interface BillingRunExecutionUseCase {
    NightlyBillingExecutionResponse executeTodaysNightlyBillingRunUnlessItExists();
}
