package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.identity.service.CurrentPersonService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class DiscountActorResolver {
    private static final String DIRECTOR_AUTHORITY = "ROLE_DIRECTOR";

    private final CurrentPersonService currentPersonService;

    public DiscountActorResolver(CurrentPersonService currentPersonService) {
        this.currentPersonService = currentPersonService;
    }

    public Long currentPersonId() {
        return currentPersonService.resolveCurrentPerson().getId();
    }

    public boolean currentActorIsDirector() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream().anyMatch(authority -> DIRECTOR_AUTHORITY.equals(authority.getAuthority()));
    }
}
