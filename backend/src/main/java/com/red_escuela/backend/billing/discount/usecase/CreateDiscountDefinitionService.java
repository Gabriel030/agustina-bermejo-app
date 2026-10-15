package com.red_escuela.backend.billing.discount.usecase;

import com.red_escuela.backend.billing.discount.domain.DiscountDefinition;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionDraft;
import com.red_escuela.backend.billing.discount.service.DiscountDefinitionOperationRunner;
import com.red_escuela.backend.billing.operation.BillingOperationOutcome;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.security.access.prepost.PreAuthorize;

@UseCase
public class CreateDiscountDefinitionService implements CreateDiscountDefinitionUseCase {
    private final DiscountDefinitionOperationRunner operationRunner;

    public CreateDiscountDefinitionService(DiscountDefinitionOperationRunner operationRunner) {
        this.operationRunner = operationRunner;
    }

    @Override
    @PreAuthorize("hasAnyRole('DIRECTOR', 'ADMINISTRATIVO')")
    public BillingOperationOutcome<DiscountDefinition> createDiscountDefinition(DiscountDefinitionDraft draft) {
        return operationRunner.runCreation(draft);
    }
}
