package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.billing.discount.domain.DiscountDefinitionDraft;
import com.red_escuela.backend.billing.discount.operation.CreateDiscountDefinitionCommand;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescriber;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription.DescriptionLine;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription.DetailBlock;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription.EffectLine;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription.EffectTone;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
public class CreateDiscountDefinitionAuthorizationRequestDescriber implements AuthorizationRequestDescriber<CreateDiscountDefinitionCommand> {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Override
    public BillingOperationType type() {
        return BillingOperationType.CREATE_DISCOUNT_DEFINITION;
    }

    @Override
    public AuthorizationRequestDescription describe(CreateDiscountDefinitionCommand command, Long authorizationRequestId) {
        DiscountDefinitionDraft draft = command.draft();
        List<DescriptionLine> lines = new ArrayList<>();
        lines.add(new DescriptionLine("Promoción", draft.name().strip()));
        lines.add(new DescriptionLine("Tipo", draft.discountType().name()));
        if (draft.calculation() != null) {
            lines.add(new DescriptionLine("Beneficio", DiscountAdjustmentText.of(draft.calculation())));
        }
        if (draft.availableFrom() != null) {
            lines.add(new DescriptionLine("Disponible desde", DAY.format(draft.availableFrom())));
        }
        if (draft.availableUntil() != null) {
            lines.add(new DescriptionLine("Disponible hasta", DAY.format(draft.availableUntil())));
        }
        List<EffectLine> effects = List.of(new EffectLine(EffectTone.SUCCESS, "La promoción queda activa en el catálogo."));
        return new AuthorizationRequestDescription("Alta de la promoción " + draft.name().strip(), null, null, lines, effects, DetailBlock.none());
    }
}
