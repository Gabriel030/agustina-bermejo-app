package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.repository.RoleRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class PermissionGridRoles {

    static final String DIRECTOR = "DIRECTOR";
    private static final String PLATFORM_ADMIN = "PLATFORM_ADMIN";

    private final RoleRepository roleRepository;

    public PermissionGridRoles(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    public static boolean isCurrentUserDirector() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated()
                && authentication.getAuthorities().stream().anyMatch(authority -> ("ROLE_" + DIRECTOR).equals(authority.getAuthority()));
    }

    @Transactional(readOnly = true)
    public List<String> names() {
        return roleRepository.findAll().stream()
                .map(Role::getName)
                .filter(name -> !PLATFORM_ADMIN.equals(name))
                .sorted()
                .toList();
    }
}
