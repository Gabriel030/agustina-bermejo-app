package com.red_escuela.backend.billing.discount.usecase;

import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionDraft;
import com.red_escuela.backend.billing.discount.service.DiscountDefinitionOperationRunner;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@UseCase
public class ValidateDiscountDefinitionService implements ValidateDiscountDefinitionUseCase {
    private final DiscountDefinitionOperationRunner operationRunner;

    public ValidateDiscountDefinitionService(DiscountDefinitionOperationRunner operationRunner) {
        this.operationRunner = operationRunner;
    }

    @Override
    @PreAuthorize("hasAnyRole('DIRECTOR', 'ADMINISTRATIVO')")
    public List<PolicyViolation> validateDiscountDefinition(DiscountDefinitionDraft draft) {
        return operationRunner.collectCreationViolations(draft);
    }
}
