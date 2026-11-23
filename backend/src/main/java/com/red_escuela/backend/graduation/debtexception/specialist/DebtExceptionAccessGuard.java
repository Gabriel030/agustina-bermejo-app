package com.red_escuela.backend.graduation.debtexception.specialist;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class DebtExceptionAccessGuard {

    private static final String DIRECTOR = "ROLE_DIRECTOR";
    private static final String SUBDIRECTOR = "ROLE_SUBDIRECTOR";

    public void requireNotSubdirector() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return;
        }
        boolean subdirector = false;
        boolean director = false;
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            subdirector |= SUBDIRECTOR.equals(authority.getAuthority());
            director |= DIRECTOR.equals(authority.getAuthority());
        }
        if (subdirector && !director) {
            throw new AccessDeniedException("Acceso denegado");
        }
    }
}
