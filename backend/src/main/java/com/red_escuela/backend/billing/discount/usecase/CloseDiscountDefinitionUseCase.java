package com.red_escuela.backend.billing.discount.usecase;

import com.red_escuela.backend.billing.discount.domain.DiscountDefinition;
import com.red_escuela.backend.billing.operation.BillingOperationOutcome;

public interface CloseDiscountDefinitionUseCase {
    BillingOperationOutcome<DiscountDefinition> closeDiscountDefinition(Long discountDefinitionId, String reason);
}
