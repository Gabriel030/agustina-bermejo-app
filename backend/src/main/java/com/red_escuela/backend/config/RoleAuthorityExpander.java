package com.red_escuela.backend.config;

import com.red_escuela.backend.shared.permission.RoleInheritance;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class RoleAuthorityExpander {

    private RoleAuthorityExpander() {
    }

    public static Set<String> expand(List<String> roles) {
        Set<String> expanded = new LinkedHashSet<>(roles);
        roles.forEach(role -> expanded.addAll(RoleInheritance.inheritedBy(role)));
        return expanded;
    }
}
