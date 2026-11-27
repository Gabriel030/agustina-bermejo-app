package com.red_escuela.backend.attendance.specialist;

import com.red_escuela.backend.attendance.domain.AttendanceChangeRequestScope;
import com.red_escuela.backend.identity.api.PreceptorScopeFinder;
import com.red_escuela.backend.shared.permission.PermissionGate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class AttendanceChangeRequestScopeResolver {

    private static final String FULL_QUEUE_PERMISSION = "attendance.read_all_change_requests";
    private static final String FULL_QUEUE_LEGACY_ROLES = "ROLE_ADMINISTRATIVO,ROLE_SUBDIRECTOR,ROLE_DIRECTOR";

    private final PreceptorScopeFinder preceptorScopeFinder;
    private final PermissionGate permissionGate;

    public AttendanceChangeRequestScopeResolver(PreceptorScopeFinder preceptorScopeFinder, PermissionGate permissionGate) {
        this.preceptorScopeFinder = preceptorScopeFinder;
        this.permissionGate = permissionGate;
    }

    public AttendanceChangeRequestScope currentScope() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return AttendanceChangeRequestScope.limitedTo(Set.of(), Set.of(), Set.of());
        }
        if (permissionGate.allows(authentication, FULL_QUEUE_PERMISSION, FULL_QUEUE_LEGACY_ROLES)) {
            return AttendanceChangeRequestScope.everything();
        }
        Long personId = Long.valueOf(authentication.getName());
        return AttendanceChangeRequestScope.limitedTo(
                preceptorScopeFinder.assignedSubjectSectionIds(personId),
                preceptorScopeFinder.assignedCourseSectionIds(personId),
                preceptorScopeFinder.assignedProgramCohortIds(personId));
    }
}
