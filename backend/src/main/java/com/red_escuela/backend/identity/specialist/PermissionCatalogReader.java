package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.identity.dto.PermissionCatalogResponse;
import com.red_escuela.backend.identity.dto.PermissionModuleResponse;
import com.red_escuela.backend.identity.dto.PermissionResponse;
import com.red_escuela.backend.identity.entity.Permission;
import com.red_escuela.backend.identity.repository.PermissionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class PermissionCatalogReader {

    private final PermissionRepository permissionRepository;

    public PermissionCatalogReader(PermissionRepository permissionRepository) {
        this.permissionRepository = permissionRepository;
    }

    @Transactional(readOnly = true)
    public PermissionCatalogResponse read() {
        Map<String, List<PermissionResponse>> byModule = new LinkedHashMap<>();
        for (Permission permission : permissionRepository.findAllByOrderByModuleAscCodeAsc()) {
            byModule.computeIfAbsent(permission.getModule(), key -> new ArrayList<>())
                    .add(new PermissionResponse(permission.getCode(), permission.getDescription(), permission.isFixedToDirector()));
        }
        return new PermissionCatalogResponse(byModule.entrySet().stream()
                .map(entry -> new PermissionModuleResponse(entry.getKey(), List.copyOf(entry.getValue())))
                .toList());
    }
}
