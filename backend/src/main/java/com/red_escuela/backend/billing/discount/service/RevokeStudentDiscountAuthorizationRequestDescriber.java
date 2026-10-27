package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.billing.discount.operation.RevokeStudentDiscountCommand;
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
public class RevokeStudentDiscountAuthorizationRequestDescriber implements AuthorizationRequestDescriber<RevokeStudentDiscountCommand> {

    @Override
    public BillingOperationType type() {
        return BillingOperationType.REVOKE_STUDENT_DISCOUNT;
    }

    @Override
    public AuthorizationRequestDescription describe(RevokeStudentDiscountCommand command, Long authorizationRequestId) {
        List<DescriptionLine> lines = new ArrayList<>();
        lines.add(new DescriptionLine("Descuento", "N.º " + command.studentDiscountId()));
        if (command.reason() != null && !command.reason().isBlank()) {
            lines.add(new DescriptionLine("Motivo de la revocación", command.reason().strip()));
        }
        List<EffectLine> effects = List.of(new EffectLine(EffectTone.WARNING, "Se revoca el descuento del alumno."));
        return new AuthorizationRequestDescription("Revocar el descuento " + command.studentDiscountId(), null, null, lines, effects, DetailBlock.none());
    }
}
