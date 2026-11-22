package com.red_escuela.backend.shared.permission;

import java.util.Map;
import java.util.Set;

public record PermissionSnapshot(
        Map<String, Set<String>> permissionsByRole,
        Map<String, PermissionMode> modeByRoleAndModule,
        Set<String> allCodes) {

    public static final String DIRECTOR_ROLE = "DIRECTOR";
    public static final PermissionSnapshot EMPTY = new PermissionSnapshot(Map.of(), Map.of(), Set.of());

    public static String modeKey(String role, String module) {
        return role + "|" + module;
    }

    public PermissionMode modeOf(String role, String module) {
        return modeByRoleAndModule.getOrDefault(modeKey(role, module), PermissionMode.LEGACY);
    }

    public boolean grants(String role, String permission) {
        if (DIRECTOR_ROLE.equals(role)) {
            return allCodes.contains(permission);
        }
        return permissionsByRole.getOrDefault(role, Set.of()).contains(permission);
    }
}
