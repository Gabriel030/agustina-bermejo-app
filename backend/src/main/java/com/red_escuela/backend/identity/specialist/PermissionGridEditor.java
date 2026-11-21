package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.identity.dto.PermissionGridChange;
import com.red_escuela.backend.identity.dto.UpdatePermissionGridRequest;
import com.red_escuela.backend.identity.entity.Permission;
import com.red_escuela.backend.identity.entity.RolePermission;
import com.red_escuela.backend.identity.exception.DirectorPermissionsNotEditableException;
import com.red_escuela.backend.identity.exception.DuplicatePermissionGridChangeException;
import com.red_escuela.backend.identity.exception.PermissionFixedToDirectorException;
import com.red_escuela.backend.identity.exception.PermissionGridForbiddenException;
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
        validate(request.changes(), gridRoles.names(), permissionByCode);
        for (PermissionGridChange change : request.changes()) {
            applyChange(change, permissionByCode.get(change.permissionCode()), request.reason());
        }
        invalidateSnapshotAfterCommit();
    }

    private void validate(List<PermissionGridChange> changes, List<String> knownRoles,
                          Map<String, Permission> permissionByCode) {
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
            if (permission.isFixedToDirector()) {
                throw new PermissionFixedToDirectorException(permission.getCode());
            }
            if (PermissionGridRoles.DIRECTOR.equals(change.role())) {
                throw new DirectorPermissionsNotEditableException();
            }
        }
    }

    private void applyChange(PermissionGridChange change, Permission permission, String reason) {
        var existing = rolePermissionRepository.findByRoleAndPermissionId(change.role(), permission.getId());
        if (change.granted() && existing.isEmpty()) {
            rolePermissionRepository.save(RolePermission.builder()
                    .role(change.role()).permissionId(permission.getId()).build());
            auditRecorder.recordPermissionGridChanged(permission.getId(), change.role(), true, reason);
        } else if (!change.granted() && existing.isPresent()) {
            rolePermissionRepository.delete(existing.get());
            auditRecorder.recordPermissionGridChanged(permission.getId(), change.role(), false, reason);
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
