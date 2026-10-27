package com.red_escuela.backend.billing.authorization.service;

import com.red_escuela.backend.billing.authorization.domain.AuthorizationInboxAccessDeniedException;
import com.red_escuela.backend.billing.authorization.domain.AuthorizationRequest;
import com.red_escuela.backend.billing.authorization.domain.AuthorizationRequestAccessDeniedException;
import com.red_escuela.backend.billing.authorization.domain.OwnAuthorizationRequestDecisionException;
import com.red_escuela.backend.billing.operation.AuthorizationRole;
import com.red_escuela.backend.identity.entity.Person;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class AuthorizationCallerAccess {
    private static final String ADMINISTRATIVE_ROLE = "ADMINISTRATIVO";

    public InboxScope scopeOfCurrentCaller() {
        Set<String> roles = currentRoleNames();
        if (AuthorizationRole.DIRECTOR.isSatisfiedByAnyOf(roles)) {
            return InboxScope.everyRequest();
        }
        if (roles.contains(ADMINISTRATIVE_ROLE)) {
            return InboxScope.requestsOf(Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getName()));
        }
        throw new AuthorizationInboxAccessDeniedException();
    }

    public void requireMayRead(AuthorizationRequest request) {
        InboxScope scope = scopeOfCurrentCaller();
        if (!scope.includes(request)) {
            throw new AuthorizationInboxAccessDeniedException(request.getId());
        }
    }

    public void requireMayDecide(AuthorizationRequest request, Person decider) {
        if (!AuthorizationRole.DIRECTOR.isSatisfiedByAnyOf(currentRoleNames())) {
            throw new AuthorizationRequestAccessDeniedException(request.getId());
        }
        if (request.getRequestedBy().getId().equals(decider.getId())) {
            throw new OwnAuthorizationRequestDecisionException(request.getId());
        }
    }

    public void requireIsRequester(AuthorizationRequest request, Person person) {
        if (!request.getRequestedBy().getId().equals(person.getId())) {
            throw new AuthorizationRequestAccessDeniedException(request.getId());
        }
    }

    private Set<String> currentRoleNames() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(authority -> authority.replaceFirst("^ROLE_", ""))
                .collect(Collectors.toSet());
    }

    public record InboxScope(Long onlyRequestedBy) {
        static InboxScope everyRequest() {
            return new InboxScope(null);
        }

        static InboxScope requestsOf(Long personId) {
            return new InboxScope(personId);
        }

        public boolean includes(AuthorizationRequest request) {
            return onlyRequestedBy == null || onlyRequestedBy.equals(request.getRequestedBy().getId());
        }
    }
}
