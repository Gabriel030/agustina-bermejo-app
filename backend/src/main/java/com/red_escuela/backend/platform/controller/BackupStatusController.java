package com.red_escuela.backend.platform.controller;

import com.red_escuela.backend.platform.domain.BackupStatusViewer;
import com.red_escuela.backend.platform.dto.BackupStatusResponse;
import com.red_escuela.backend.platform.usecase.backup.GetBackupStatusUseCase;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BackupStatusController {

    private static final String PLATFORM_ADMIN_AUTHORITY = "ROLE_PLATFORM_ADMIN";

    private final GetBackupStatusUseCase getBackupStatus;

    public BackupStatusController(GetBackupStatusUseCase getBackupStatus) {
        this.getBackupStatus = getBackupStatus;
    }

    @GetMapping("/backups/status")
    @PreAuthorize("hasAnyRole('DIRECTOR','PLATFORM_ADMIN')")
    public BackupStatusResponse getStatus(Authentication authentication) {
        return BackupStatusResponse.of(getBackupStatus.read(viewerOf(authentication)));
    }

    private BackupStatusViewer viewerOf(Authentication authentication) {
        boolean platformAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority -> PLATFORM_ADMIN_AUTHORITY.equals(authority.getAuthority()));
        return platformAdmin ? BackupStatusViewer.PLATFORM_ADMIN : BackupStatusViewer.DIRECTOR;
    }
}
