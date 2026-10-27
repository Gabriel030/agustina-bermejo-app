package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.billing.discount.domain.StudentDiscountGrantRequest;
import com.red_escuela.backend.billing.discount.operation.GrantStudentDiscountCommand;
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
public class GrantStudentDiscountAuthorizationRequestDescriber implements AuthorizationRequestDescriber<GrantStudentDiscountCommand> {

    @Override
    public BillingOperationType type() {
        return BillingOperationType.GRANT_STUDENT_DISCOUNT;
    }

    @Override
    public AuthorizationRequestDescription describe(GrantStudentDiscountCommand command, Long authorizationRequestId) {
        StudentDiscountGrantRequest request = command.request();
        String benefit = request.calculation() == null ? null : DiscountAdjustmentText.of(request.calculation());
        List<DescriptionLine> lines = new ArrayList<>();
        lines.add(new DescriptionLine("Inscripción", "N.º " + command.enrollment().id()));
        lines.add(new DescriptionLine("Descuento", request.discountType().name()));
        if (benefit != null) {
            lines.add(new DescriptionLine("Beneficio", benefit));
        }
        if (request.fromPeriod() != null) {
            lines.add(new DescriptionLine("Desde", "Cuota " + DiscountAdjustmentText.periodOf(request.fromPeriod())));
        }
        if (request.toPeriod() != null) {
            lines.add(new DescriptionLine("Hasta", "Cuota " + DiscountAdjustmentText.periodOf(request.toPeriod())));
        }
        List<EffectLine> effects = List.of(new EffectLine(EffectTone.SUCCESS, "Se otorga el descuento a la inscripción."));
        return new AuthorizationRequestDescription(benefit == null ? "Alta de descuento" : "Alta de descuento de " + benefit, null, null, lines, effects, DetailBlock.none());
    }
}
