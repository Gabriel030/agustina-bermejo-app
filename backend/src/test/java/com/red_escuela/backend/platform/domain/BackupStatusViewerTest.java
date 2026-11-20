package com.red_escuela.backend.platform.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BackupStatusViewerTest {

    @Test
    void directorAuthorityResolvesToDirector() {
        assertThat(BackupStatusViewer.fromAuthorities(List.of("ROLE_DIRECTOR"))).isEqualTo(BackupStatusViewer.DIRECTOR);
    }

    @Test
    void platformAdminAuthorityResolvesToPlatformAdmin() {
        assertThat(BackupStatusViewer.fromAuthorities(List.of("ROLE_PLATFORM_ADMIN")))
                .isEqualTo(BackupStatusViewer.PLATFORM_ADMIN);
    }

    @Test
    void userWithBothRolesResolvesToPlatformAdmin() {
        assertThat(BackupStatusViewer.fromAuthorities(List.of("ROLE_DIRECTOR", "ROLE_PLATFORM_ADMIN")))
                .isEqualTo(BackupStatusViewer.PLATFORM_ADMIN);
    }

    @Test
    void onlyPlatformAdminSeesTechnicalFields() {
        assertThat(BackupStatusViewer.PLATFORM_ADMIN.seesTechnicalFields()).isTrue();
        assertThat(BackupStatusViewer.DIRECTOR.seesTechnicalFields()).isFalse();
    }
}
