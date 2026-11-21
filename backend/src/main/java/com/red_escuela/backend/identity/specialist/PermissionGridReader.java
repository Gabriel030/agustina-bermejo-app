package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.identity.domain.PermissionModuleMode;
import com.red_escuela.backend.identity.dto.PermissionGridResponse;
import com.red_escuela.backend.identity.dto.PermissionModuleModeResponse;
import com.red_escuela.backend.identity.dto.RolePermissionsResponse;
import com.red_escuela.backend.identity.entity.Permission;
import com.red_escuela.backend.identity.entity.PermissionModuleModeSetting;
import com.red_escuela.backend.identity.entity.RolePermission;
import com.red_escuela.backend.identity.repository.PermissionModuleModeSettingRepository;
import com.red_escuela.backend.identity.repository.PermissionRepository;
import com.red_escuela.backend.identity.repository.RolePermissionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class PermissionGridReader {

    private final PermissionGridRoles gridRoles;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionModuleModeSettingRepository modeRepository;

    public PermissionGridReader(PermissionGridRoles gridRoles, PermissionRepository permissionRepository,
                                RolePermissionRepository rolePermissionRepository,
                                PermissionModuleModeSettingRepository modeRepository) {
        this.gridRoles = gridRoles;
        this.permissionRepository = permissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.modeRepository = modeRepository;
    }

    @Transactional(readOnly = true)
    public PermissionGridResponse read() {
        List<String> roles = gridRoles.names();
        List<Permission> permissions = permissionRepository.findAllByOrderByModuleAscCodeAsc();
        Map<Long, Permission> permissionById = permissions.stream()
                .collect(Collectors.toMap(Permission::getId, Function.identity()));
        Map<String, TreeSet<String>> grantedByRole = new HashMap<>();
        for (RolePermission grant : rolePermissionRepository.findAll()) {
            Permission permission = permissionById.get(grant.getPermissionId());
            if (permission != null && !permission.isFixedToDirector()) {
                grantedByRole.computeIfAbsent(grant.getRole(), key -> new TreeSet<>()).add(permission.getCode());
            }
        }
        List<RolePermissionsResponse> roleRows = new ArrayList<>();
        for (String role : roles) {
            List<String> codes = PermissionGridRoles.DIRECTOR.equals(role)
                    ? permissions.stream().map(Permission::getCode).sorted().toList()
                    : List.copyOf(grantedByRole.getOrDefault(role, new TreeSet<>()));
            roleRows.add(new RolePermissionsResponse(role, codes));
        }
        Map<String, PermissionModuleMode> storedModes = new HashMap<>();
        for (PermissionModuleModeSetting setting : modeRepository.findAll()) {
            storedModes.put(setting.getRole() + "|" + setting.getModule(), PermissionModuleMode.valueOf(setting.getMode()));
        }
        List<String> modules = permissions.stream().map(Permission::getModule).distinct().toList();
        List<PermissionModuleModeResponse> modeRows = new ArrayList<>();
        for (String role : roles) {
            for (String module : modules) {
                modeRows.add(new PermissionModuleModeResponse(role, module,
                        storedModes.getOrDefault(role + "|" + module, PermissionModuleMode.LEGACY)));
            }
        }
        return new PermissionGridResponse(List.copyOf(roleRows), List.copyOf(modeRows),
                PermissionGridRoles.isCurrentUserDirector());
    }
}
