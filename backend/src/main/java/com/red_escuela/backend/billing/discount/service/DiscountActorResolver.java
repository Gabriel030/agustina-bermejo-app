package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.identity.service.CurrentPersonService;
import org.springframework.stereotype.Component;

@Component
public class DiscountActorResolver {
    private final CurrentPersonService currentPersonService;

    public DiscountActorResolver(CurrentPersonService currentPersonService) {
        this.currentPersonService = currentPersonService;
    }

    public Long currentPersonId() {
        return currentPersonService.resolveCurrentPerson().getId();
    }
}
