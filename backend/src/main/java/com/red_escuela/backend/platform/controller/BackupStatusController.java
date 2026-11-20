package com.red_escuela.backend.platform.controller;

import com.red_escuela.backend.platform.domain.BackupStatusViewer;
import com.red_escuela.backend.platform.dto.BackupStatusResponse;
import com.red_escuela.backend.platform.usecase.backup.GetBackupStatusUseCase;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BackupStatusController {

    private final GetBackupStatusUseCase getBackupStatus;

    public BackupStatusController(GetBackupStatusUseCase getBackupStatus) {
        this.getBackupStatus = getBackupStatus;
    }

    @GetMapping("/backups/status")
    @PreAuthorize("hasAnyRole('DIRECTOR','PLATFORM_ADMIN')")
    public BackupStatusResponse getStatus(Authentication authentication) {
        BackupStatusViewer viewer = BackupStatusViewer.fromAuthorities(authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).toList());
        return BackupStatusResponse.of(getBackupStatus.read(viewer), viewer);
    }
}
