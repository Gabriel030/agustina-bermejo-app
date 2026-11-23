package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.identity.entity.Permission;
import com.red_escuela.backend.identity.entity.PermissionModuleModeSetting;
import com.red_escuela.backend.identity.entity.RolePermission;
import com.red_escuela.backend.identity.repository.PermissionModuleModeSettingRepository;
import com.red_escuela.backend.identity.repository.PermissionRepository;
import com.red_escuela.backend.identity.repository.RolePermissionRepository;
import com.red_escuela.backend.shared.permission.PermissionMode;
import com.red_escuela.backend.shared.permission.PermissionPolicyStore;
import com.red_escuela.backend.shared.permission.PermissionSnapshot;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class TablePermissionPolicyStore implements PermissionPolicyStore {

    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionModuleModeSettingRepository modeRepository;

    public TablePermissionPolicyStore(PermissionRepository permissionRepository,
                                      RolePermissionRepository rolePermissionRepository,
                                      PermissionModuleModeSettingRepository modeRepository) {
        this.permissionRepository = permissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.modeRepository = modeRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public PermissionSnapshot load() {
        List<Permission> permissions = permissionRepository.findAll();
        Map<Long, Permission> permissionById = permissions.stream()
                .collect(Collectors.toMap(Permission::getId, Function.identity()));
        Map<String, Set<String>> permissionsByRole = new HashMap<>();
        for (RolePermission grant : rolePermissionRepository.findAll()) {
            Permission permission = permissionById.get(grant.getPermissionId());
            if (permission != null) {
                permissionsByRole.computeIfAbsent(grant.getRole(), key -> new HashSet<>()).add(permission.getCode());
            }
        }
        Map<String, PermissionMode> modes = new HashMap<>();
        for (PermissionModuleModeSetting setting : modeRepository.findAll()) {
            modes.put(PermissionSnapshot.modeKey(setting.getRole(), setting.getModule()), PermissionMode.valueOf(setting.getMode()));
        }
        Set<String> allCodes = permissions.stream().map(Permission::getCode).collect(Collectors.toUnmodifiableSet());
        return new PermissionSnapshot(Map.copyOf(permissionsByRole), Map.copyOf(modes), allCodes);
    }

}
