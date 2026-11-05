package com.red_escuela.backend.billing.payment.service;

import com.red_escuela.backend.identity.service.CurrentPersonService;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class PaymentActorResolver {
    private static final String DIRECTOR_AUTHORITY = "ROLE_DIRECTOR";

    private final CurrentPersonService currentPersonService;

    public PaymentActorResolver(CurrentPersonService currentPersonService) {
        this.currentPersonService = currentPersonService;
    }

    public StatusChangeActor currentPersonAsActor() {
        return StatusChangeActor.ofPerson(currentPersonService.resolveCurrentPerson().getId());
    }

    public boolean currentActorIsDirector() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream().anyMatch(authority -> DIRECTOR_AUTHORITY.equals(authority.getAuthority()));
    }
}
