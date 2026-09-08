package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.dto.CreatePaymentPlanOptionRequest;
import com.red_escuela.backend.billing.entity.PaymentPlanOption;
import com.red_escuela.backend.enrollment.exception.InvalidWindowConfigurationException;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import org.springframework.stereotype.Service;

@Service
public class PaymentPlanOptionBuilder {

    private final CurrentPersonService currentPersonService;

    public PaymentPlanOptionBuilder(CurrentPersonService currentPersonService) {
        this.currentPersonService = currentPersonService;
    }

    public PaymentPlanOption build(CreatePaymentPlanOptionRequest request) {
        validateUpfrontWindow(request);
        Person actor = currentPersonService.resolveCurrentPerson();
        PaymentPlanOption option = new PaymentPlanOption();
        option.setDescription(request.description());
        option.setCadence(request.cadence());
        option.setUpfrontDiscount(request.upfrontDiscount() == null ? null : request.upfrontDiscount().toValue());
        option.setUpfrontWindowEnabled(request.upfrontWindowEnabled());
        option.setUpfrontWindowStart(request.upfrontWindowStart());
        option.setUpfrontWindowEnd(request.upfrontWindowEnd());
        option.setCreatedBy(actor);
        return option;
    }

    // Sin esto, una PaymentPlanOption con upfrontWindowEnabled=true pero start/end nulos o mal
    // ordenados recién explotaba con NullPointerException cuando un alumno intentaba seleccionar
    // el plan — 500 en vez de rechazar la creación con 400 (hallazgo de auditoría 2026-09-04).
    private void validateUpfrontWindow(CreatePaymentPlanOptionRequest request) {
        if (!request.upfrontWindowEnabled()) {
            return;
        }
        if (request.upfrontWindowStart() == null || request.upfrontWindowEnd() == null) {
            throw new InvalidWindowConfigurationException(
                    "upfrontWindowEnabled=true exige upfrontWindowStart y upfrontWindowEnd no nulos");
        }
        if (request.upfrontWindowStart().isAfter(request.upfrontWindowEnd())) {
            throw new InvalidWindowConfigurationException(
                    "upfrontWindowStart no puede ser posterior a upfrontWindowEnd");
        }
    }
}
