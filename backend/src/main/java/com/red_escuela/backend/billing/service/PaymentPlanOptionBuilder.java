package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
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

    public PaymentPlanOption build(CreatePaymentPlanOptionRequest request, Currency currency) {
        validateUpfrontWindow(request);
        Person actor = currentPersonService.resolveCurrentPerson();
        PaymentPlanOption option = new PaymentPlanOption();
        option.setDescription(request.description());
        option.setCadence(request.cadence());
        MonetaryAdjustment discount = request.upfrontDiscount() == null ? null : request.upfrontDiscount().toAdjustment();
        if (discount != null) {
            discount.requireCurrency(currency);
        }
        option.setUpfrontDiscount(discount);
        option.setUpfrontWindowEnabled(request.upfrontWindowEnabled());
        option.setUpfrontWindowStart(request.upfrontWindowStart());
        option.setUpfrontWindowEnd(request.upfrontWindowEnd());
        option.setCreatedBy(actor);
        return option;
    }

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
