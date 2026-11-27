package com.red_escuela.backend.attendance.service;

import com.red_escuela.backend.attendance.domain.AttendanceChangeRequestScope;
import com.red_escuela.backend.identity.api.PreceptorScopeFinder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class AttendanceChangeRequestScopeResolver {

    private static final Set<String> FULL_QUEUE_AUTHORITIES = Set.of("ROLE_DIRECTOR", "ROLE_ADMINISTRATIVO", "ROLE_SUBDIRECTOR");

    private final PreceptorScopeFinder preceptorScopeFinder;

    public AttendanceChangeRequestScopeResolver(PreceptorScopeFinder preceptorScopeFinder) {
        this.preceptorScopeFinder = preceptorScopeFinder;
    }

    public AttendanceChangeRequestScope currentScope() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return AttendanceChangeRequestScope.limitedTo(Set.of(), Set.of());
        }
        boolean seesFullQueue = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(FULL_QUEUE_AUTHORITIES::contains);
        if (seesFullQueue) {
            return AttendanceChangeRequestScope.everything();
        }
        Long personId = Long.valueOf(authentication.getName());
        return AttendanceChangeRequestScope.limitedTo(
                preceptorScopeFinder.assignedProgramIds(personId), preceptorScopeFinder.assignedCourseIds(personId));
    }
}
