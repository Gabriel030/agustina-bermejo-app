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
    void emptyAuthoritiesResolveToDirector() {
        assertThat(BackupStatusViewer.fromAuthorities(List.of())).isEqualTo(BackupStatusViewer.DIRECTOR);
    }

    @Test
    void platformAdminWithoutRolePrefixResolvesToDirector() {
        assertThat(BackupStatusViewer.fromAuthorities(List.of("PLATFORM_ADMIN"))).isEqualTo(BackupStatusViewer.DIRECTOR);
    }

    @Test
    void unknownAuthorityResolvesToDirector() {
        assertThat(BackupStatusViewer.fromAuthorities(List.of("ROLE_UNKNOWN"))).isEqualTo(BackupStatusViewer.DIRECTOR);
    }

    @Test
    void onlyPlatformAdminSeesTechnicalFields() {
        assertThat(BackupStatusViewer.PLATFORM_ADMIN.seesTechnicalFields()).isTrue();
        assertThat(BackupStatusViewer.DIRECTOR.seesTechnicalFields()).isFalse();
    }
}
