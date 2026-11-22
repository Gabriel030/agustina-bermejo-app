package com.red_escuela.backend.shared.module;

import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

@Component
public class CatalogPriceVisibilityPolicy {

    private static final String STUDENT_AUTHORITY = "ROLE_ALUMNO";

    private final CurrentBillingModePort billingMode;

    public CatalogPriceVisibilityPolicy(CurrentBillingModePort billingMode) {
        this.billingMode = billingMode;
    }

    public boolean hidesPrices(HttpMethod method, Authentication authentication) {
        return HttpMethod.GET.equals(method)
                && !billingMode.currentMode().isEnabled()
                && isStudentOnly(authentication);
    }

    private static boolean isStudentOnly(Authentication authentication) {
        if (authentication == null) {
            return true;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .allMatch(STUDENT_AUTHORITY::equals);
    }
}
