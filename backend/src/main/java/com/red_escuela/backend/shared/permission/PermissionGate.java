package com.red_escuela.backend.shared.permission;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component("permissionGate")
public class PermissionGate {

    private static final String ROLE_PREFIX = "ROLE_";

    private final PermissionSnapshotCache cache;
    private final PermissionDiscrepancySink discrepancySink;

    public PermissionGate(PermissionSnapshotCache cache, PermissionDiscrepancySink discrepancySink) {
        this.cache = cache;
        this.discrepancySink = discrepancySink;
    }

    public boolean allows(Authentication authentication, String permission, String... legacyRoles) {
        return decide(authentication, permission, false, legacyRoles);
    }

    public boolean allowsWithoutInheritance(Authentication authentication, String permission, String... legacyRoles) {
        return decide(authentication, permission, true, legacyRoles);
    }

    private boolean decide(Authentication authentication, String permission, boolean ignoreInheritedRoles, String[] legacyRoles) {
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            return false;
        }
        Set<String> legacy = Arrays.stream(legacyRoles).flatMap(entry -> Arrays.stream(entry.split(","))).map(String::trim).collect(Collectors.toUnmodifiableSet());
        String module = moduleOf(permission);
        PermissionSnapshot snapshot = cache.current();
        Set<String> inherited = ignoreInheritedRoles ? inheritedRolesOf(authentication) : Set.of();
        boolean allowed = false;
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String name = authority.getAuthority();
            if (name == null || !name.startsWith(ROLE_PREFIX)) {
                continue;
            }
            String role = name.substring(ROLE_PREFIX.length());
            if (inherited.contains(role)) {
                continue;
            }
            boolean legacyDecision = legacy.contains(name) || legacy.contains(role);
            PermissionMode mode = snapshot.modeOf(role, module);
            if (mode == PermissionMode.LEGACY) {
                allowed |= legacyDecision;
                continue;
            }
            boolean permissionDecision = snapshot.grants(role, permission);
            if (mode == PermissionMode.SHADOW) {
                if (permissionDecision != legacyDecision) {
                    discrepancySink.record(role, permission, legacyDecision, permissionDecision);
                }
                allowed |= legacyDecision;
            } else {
                allowed |= permissionDecision;
            }
        }
        return allowed;
    }

    private static Set<String> inheritedRolesOf(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(name -> name != null && name.startsWith(ROLE_PREFIX))
                .flatMap(name -> RoleInheritance.inheritedBy(name.substring(ROLE_PREFIX.length())).stream())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String moduleOf(String permission) {
        int dot = permission.indexOf('.');
        return dot < 0 ? permission : permission.substring(0, dot);
    }
}
