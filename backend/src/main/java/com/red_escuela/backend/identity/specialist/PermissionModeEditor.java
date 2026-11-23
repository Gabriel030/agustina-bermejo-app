package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.identity.domain.PermissionModuleMode;
import com.red_escuela.backend.identity.dto.PermissionModeChange;
import com.red_escuela.backend.identity.dto.UpdatePermissionModesRequest;
import com.red_escuela.backend.identity.entity.PermissionModuleModeSetting;
import com.red_escuela.backend.identity.exception.DirectorPermissionsNotEditableException;
import com.red_escuela.backend.identity.exception.DuplicatePermissionModeChangeException;
import com.red_escuela.backend.identity.exception.PermissionGridForbiddenException;
import com.red_escuela.backend.identity.exception.UnknownPermissionModuleException;
import com.red_escuela.backend.identity.exception.UnknownPermissionRoleException;
import com.red_escuela.backend.identity.repository.PermissionModuleModeSettingRepository;
import com.red_escuela.backend.identity.repository.PermissionRepository;
import com.red_escuela.backend.shared.permission.PermissionSnapshotCache;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class PermissionModeEditor {

    private final PermissionGridRoles gridRoles;
    private final PermissionRepository permissionRepository;
    private final PermissionModuleModeSettingRepository modeRepository;
    private final IdentityAuditRecorder auditRecorder;
    private final PermissionSnapshotCache snapshotCache;

    public PermissionModeEditor(PermissionGridRoles gridRoles, PermissionRepository permissionRepository,
                                PermissionModuleModeSettingRepository modeRepository,
                                IdentityAuditRecorder auditRecorder, PermissionSnapshotCache snapshotCache) {
        this.gridRoles = gridRoles;
        this.permissionRepository = permissionRepository;
        this.modeRepository = modeRepository;
        this.auditRecorder = auditRecorder;
        this.snapshotCache = snapshotCache;
    }

    @Transactional
    public void edit(UpdatePermissionModesRequest request) {
        if (!PermissionGridRoles.isCurrentUserDirector()) {
            throw new PermissionGridForbiddenException();
        }
        validate(request.changes(), gridRoles.names(), permissionRepository.findDistinctModules());
        for (PermissionModeChange change : request.changes()) {
            applyChange(change, request.reason());
        }
        invalidateSnapshotAfterCommit();
    }

    private void validate(List<PermissionModeChange> changes, List<String> knownRoles, List<String> knownModules) {
        Set<String> seen = new HashSet<>();
        for (PermissionModeChange change : changes) {
            if (!knownRoles.contains(change.role())) {
                throw new UnknownPermissionRoleException(change.role());
            }
            if (!knownModules.contains(change.module())) {
                throw new UnknownPermissionModuleException(change.module());
            }
            if (!seen.add(change.role() + "|" + change.module())) {
                throw new DuplicatePermissionModeChangeException(change.role(), change.module());
            }
            if (PermissionGridRoles.DIRECTOR.equals(change.role())) {
                throw new DirectorPermissionsNotEditableException();
            }
        }
    }

    private void applyChange(PermissionModeChange change, String reason) {
        var existing = modeRepository.findByRoleAndModule(change.role(), change.module());
        PermissionModuleMode previous = existing.map(setting -> PermissionModuleMode.valueOf(setting.getMode()))
                .orElse(PermissionModuleMode.LEGACY);
        if (previous == change.mode()) {
            return;
        }
        PermissionModuleModeSetting setting = existing.orElseGet(() -> PermissionModuleModeSetting.builder()
                .role(change.role()).module(change.module()).build());
        setting.setMode(change.mode().name());
        PermissionModuleModeSetting saved = modeRepository.save(setting);
        auditRecorder.recordPermissionModeChanged(saved.getId(), change.role(), change.module(), previous,
                change.mode(), reason);
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
