package com.red_escuela.backend.billing.discount.usecase;

import com.red_escuela.backend.billing.discount.domain.DiscountDefinition;
import com.red_escuela.backend.billing.discount.service.DiscountDefinitionOperationRunner;
import com.red_escuela.backend.billing.operation.BillingOperationOutcome;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.security.access.prepost.PreAuthorize;

@UseCase
public class CloseDiscountDefinitionService implements CloseDiscountDefinitionUseCase {
    private final DiscountDefinitionOperationRunner operationRunner;

    public CloseDiscountDefinitionService(DiscountDefinitionOperationRunner operationRunner) {
        this.operationRunner = operationRunner;
    }

    @Override
    @PreAuthorize("hasAnyRole('DIRECTOR', 'ADMINISTRATIVO')")
    public BillingOperationOutcome<DiscountDefinition> closeDiscountDefinition(Long discountDefinitionId, String reason) {
        return operationRunner.runClosure(discountDefinitionId, reason);
    }
}
