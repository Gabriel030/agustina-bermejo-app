package com.red_escuela.backend.billing.discount.usecase;

import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionDraft;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;

public interface ValidateDiscountDefinitionUseCase {
    List<PolicyViolation> validateDiscountDefinition(DiscountDefinitionDraft draft);
}
