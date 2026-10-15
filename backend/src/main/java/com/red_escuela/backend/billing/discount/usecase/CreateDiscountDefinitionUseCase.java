package com.red_escuela.backend.billing.discount.usecase;

import com.red_escuela.backend.billing.discount.domain.DiscountDefinition;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionDraft;
import com.red_escuela.backend.billing.operation.BillingOperationOutcome;

public interface CreateDiscountDefinitionUseCase {
    BillingOperationOutcome<DiscountDefinition> createDiscountDefinition(DiscountDefinitionDraft draft);
}
