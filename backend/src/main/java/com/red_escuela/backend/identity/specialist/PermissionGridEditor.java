package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.identity.dto.PermissionGridChange;
import com.red_escuela.backend.identity.dto.UpdatePermissionGridRequest;
import com.red_escuela.backend.identity.entity.Permission;
import com.red_escuela.backend.identity.entity.RolePermission;
import com.red_escuela.backend.identity.exception.DirectorPermissionsNotEditableException;
import com.red_escuela.backend.identity.exception.FixedToDirectorPermissionNotEditableException;
import com.red_escuela.backend.identity.exception.DuplicatePermissionGridChangeException;
import com.red_escuela.backend.identity.exception.PermissionGridForbiddenException;
import com.red_escuela.backend.identity.exception.SensitivePermissionConfirmationRequiredException;
import com.red_escuela.backend.identity.exception.UnknownPermissionCodeException;
import com.red_escuela.backend.identity.exception.UnknownPermissionRoleException;
import com.red_escuela.backend.identity.repository.PermissionRepository;
import com.red_escuela.backend.identity.repository.RolePermissionRepository;
import com.red_escuela.backend.shared.permission.PermissionSnapshotCache;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class PermissionGridEditor {

    private final PermissionGridRoles gridRoles;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final IdentityAuditRecorder auditRecorder;
    private final PermissionSnapshotCache snapshotCache;

    public PermissionGridEditor(PermissionGridRoles gridRoles, PermissionRepository permissionRepository,
                                RolePermissionRepository rolePermissionRepository,
                                IdentityAuditRecorder auditRecorder, PermissionSnapshotCache snapshotCache) {
        this.gridRoles = gridRoles;
        this.permissionRepository = permissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.auditRecorder = auditRecorder;
        this.snapshotCache = snapshotCache;
    }

    @Transactional
    public void edit(UpdatePermissionGridRequest request) {
        if (!PermissionGridRoles.isCurrentUserDirector()) {
            throw new PermissionGridForbiddenException();
        }
        Map<String, Permission> permissionByCode = permissionRepository.findAll().stream()
                .collect(Collectors.toMap(Permission::getCode, Function.identity()));
        validate(request.changes(), gridRoles.names(), permissionByCode, Boolean.TRUE.equals(request.sensitiveConfirmed()));
        for (PermissionGridChange change : request.changes()) {
            applyChange(change, permissionByCode.get(change.permissionCode()), request.reason());
        }
        invalidateSnapshotAfterCommit();
    }

    private void validate(List<PermissionGridChange> changes, List<String> knownRoles,
                          Map<String, Permission> permissionByCode, boolean sensitiveConfirmed) {
        Set<String> seen = new HashSet<>();
        for (PermissionGridChange change : changes) {
            if (!knownRoles.contains(change.role())) {
                throw new UnknownPermissionRoleException(change.role());
            }
            Permission permission = permissionByCode.get(change.permissionCode());
            if (permission == null) {
                throw new UnknownPermissionCodeException(change.permissionCode());
            }
            if (!seen.add(change.role() + "|" + change.permissionCode())) {
                throw new DuplicatePermissionGridChangeException(change.role(), change.permissionCode());
            }
            if (PermissionGridRoles.DIRECTOR.equals(change.role())) {
                throw new DirectorPermissionsNotEditableException();
            }
            if (permission.isFixedToDirector() && change.granted()) {
                throw new FixedToDirectorPermissionNotEditableException(permission.getCode());
            }
            if (permission.isSensitive() && !sensitiveConfirmed) {
                throw new SensitivePermissionConfirmationRequiredException(permission.getCode());
            }
        }
    }

    private void applyChange(PermissionGridChange change, Permission permission, String reason) {
        var existing = rolePermissionRepository.findByRoleAndPermissionId(change.role(), permission.getId());
        if (change.granted() && existing.isEmpty()) {
            rolePermissionRepository.save(RolePermission.builder()
                    .role(change.role()).permissionId(permission.getId()).build());
            recordChange(permission, change.role(), true, reason);
        } else if (!change.granted() && existing.isPresent()) {
            rolePermissionRepository.delete(existing.get());
            recordChange(permission, change.role(), false, reason);
        }
    }

    private void recordChange(Permission permission, String role, boolean granted, String reason) {
        if (permission.isSensitive()) {
            auditRecorder.recordPermissionSensitiveChanged(permission.getId(), permission.getCode(), role, granted, reason);
        } else {
            auditRecorder.recordPermissionGridChanged(permission.getId(), role, granted, reason);
        }
    }

    private void invalidateSnapshotAfterCommit() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            snapshotCache.invalidate();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                snapshotCache.invalidate();
            }
        });
    }
}
