package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.billing.discount.operation.CloseDiscountDefinitionCommand;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescriber;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription.DescriptionLine;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription.DetailBlock;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription.EffectLine;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription.EffectTone;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class CloseDiscountDefinitionAuthorizationRequestDescriber implements AuthorizationRequestDescriber<CloseDiscountDefinitionCommand> {

    @Override
    public BillingOperationType type() {
        return BillingOperationType.CLOSE_DISCOUNT_DEFINITION;
    }

    @Override
    public AuthorizationRequestDescription describe(CloseDiscountDefinitionCommand command, Long authorizationRequestId) {
        List<DescriptionLine> lines = new ArrayList<>();
        lines.add(new DescriptionLine("Promoción", "N.º " + command.discountDefinitionId()));
        if (command.reason() != null && !command.reason().isBlank()) {
            lines.add(new DescriptionLine("Motivo del cierre", command.reason().strip()));
        }
        List<EffectLine> effects = List.of(new EffectLine(EffectTone.WARNING, "La promoción queda cerrada."));
        return new AuthorizationRequestDescription("Cerrar la promoción " + command.discountDefinitionId(), null, null, lines, effects, DetailBlock.none());
    }
}
