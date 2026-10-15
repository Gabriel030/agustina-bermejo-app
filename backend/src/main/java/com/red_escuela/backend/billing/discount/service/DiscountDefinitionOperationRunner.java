package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.billing.discount.domain.DiscountDefinition;
import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionDraft;
import com.red_escuela.backend.billing.discount.operation.CloseDiscountDefinitionCommand;
import com.red_escuela.backend.billing.discount.operation.CloseDiscountDefinitionOperation;
import com.red_escuela.backend.billing.discount.operation.CreateDiscountDefinitionCommand;
import com.red_escuela.backend.billing.discount.operation.CreateDiscountDefinitionOperation;
import com.red_escuela.backend.billing.operation.BillingOperationOutcome;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.shared.exception.ReportsPolicyViolations;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DiscountDefinitionOperationRunner {
    private final BillingOperationPipeline pipeline;
    private final CreateDiscountDefinitionOperation createOperation;
    private final CloseDiscountDefinitionOperation closeOperation;
    private final DiscountActorResolver actorResolver;

    public DiscountDefinitionOperationRunner(BillingOperationPipeline pipeline, CreateDiscountDefinitionOperation createOperation,
                                             CloseDiscountDefinitionOperation closeOperation, DiscountActorResolver actorResolver) {
        this.pipeline = pipeline;
        this.createOperation = createOperation;
        this.closeOperation = closeOperation;
        this.actorResolver = actorResolver;
    }

    public BillingOperationOutcome<DiscountDefinition> runCreation(DiscountDefinitionDraft draft) {
        return pipeline.runOperation(createOperation, creationCommandOf(draft));
    }

    public BillingOperationOutcome<DiscountDefinition> runClosure(Long discountDefinitionId, String reason) {
        CloseDiscountDefinitionCommand command = new CloseDiscountDefinitionCommand(discountDefinitionId, reason, actorResolver.currentPersonId(),
                actorResolver.currentActorIsDirector());
        return pipeline.runOperation(closeOperation, command);
    }

    public List<PolicyViolation> collectCreationViolations(DiscountDefinitionDraft draft) {
        try {
            createOperation.requireValidContext(creationCommandOf(draft));
            return List.of();
        } catch (RuntimeException rejection) {
            if (rejection instanceof ReportsPolicyViolations reporting) {
                return reporting.violations();
            }
            throw rejection;
        }
    }

    private CreateDiscountDefinitionCommand creationCommandOf(DiscountDefinitionDraft draft) {
        return CreateDiscountDefinitionCommand.byPerson(draft, actorResolver.currentPersonId(), actorResolver.currentActorIsDirector());
    }
}
