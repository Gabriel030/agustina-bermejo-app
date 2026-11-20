package com.red_escuela.backend.platform.dto;

import com.red_escuela.backend.platform.domain.BackupAlert;
import com.red_escuela.backend.platform.domain.BackupAlertCode;
import com.red_escuela.backend.platform.domain.BackupAlertLevel;
import com.red_escuela.backend.platform.domain.BackupDisk;
import com.red_escuela.backend.platform.domain.BackupState;
import com.red_escuela.backend.platform.domain.BackupStatus;
import com.red_escuela.backend.platform.domain.BackupStatusViewer;
import com.red_escuela.backend.platform.domain.BackupSummary;
import com.red_escuela.backend.platform.domain.BackupTier;
import com.red_escuela.backend.platform.domain.BackupTierStatus;
import com.red_escuela.backend.platform.domain.BackupVerification;
import com.red_escuela.backend.platform.domain.RestoreTestFailureCode;
import com.red_escuela.backend.platform.domain.RestoreTestResult;
import com.red_escuela.backend.platform.domain.RestoreTestStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BackupStatusResponseTest {

    private static final Instant TAKEN_AT = Instant.parse("2026-11-20T08:00:00Z");
    private static final String DISK_PATH = "/var/backups/agustina";
    private static final List<String> FILE_NAMES = List.of("daily-2026-11-20.dump.age", "daily-2026-11-20-files.tar.age");

    private static BackupStatus completeStatus() {
        return new BackupStatus(BackupState.ATTENTION,
                new BackupSummary(TAKEN_AT, 9.0, 9.0, 4, 1),
                TAKEN_AT,
                List.of(new BackupTierStatus(BackupTier.DAILY, TAKEN_AT, null, 1_000, 2_000, BackupVerification.VERIFIED, 7,
                        FILE_NAMES)),
                new RestoreTestStatus(TAKEN_AT, RestoreTestResult.FAILED, RestoreTestFailureCode.COUNTS_MISMATCH),
                new BackupDisk(100, 500, DISK_PATH),
                List.of(new BackupAlert(TAKEN_AT, BackupAlertLevel.WARNING, BackupAlertCode.BACKUP_FAILED)));
    }

    @Test
    void directorReceivesTechnicalFieldsAsNull() {
        BackupStatusResponse response = BackupStatusResponse.of(completeStatus(), BackupStatusViewer.DIRECTOR);

        assertThat(response.disk().path()).isNull();
        assertThat(response.disk().freeBytes()).isEqualTo(100);
        assertThat(response.tiers()).singleElement().satisfies(tier -> {
            assertThat(tier.fileNames()).isNull();
            assertThat(tier.kept()).isEqualTo(7);
        });
    }

    @Test
    void platformAdminReceivesTechnicalFields() {
        BackupStatusResponse response = BackupStatusResponse.of(completeStatus(), BackupStatusViewer.PLATFORM_ADMIN);

        assertThat(response.disk().path()).isEqualTo(DISK_PATH);
        assertThat(response.tiers()).singleElement().satisfies(tier -> assertThat(tier.fileNames()).isEqualTo(FILE_NAMES));
    }

    @Test
    void userWithBothRolesReceivesTechnicalFields() {
        BackupStatusViewer viewer = BackupStatusViewer.fromAuthorities(List.of("ROLE_DIRECTOR", "ROLE_PLATFORM_ADMIN"));

        BackupStatusResponse response = BackupStatusResponse.of(completeStatus(), viewer);

        assertThat(response.disk().path()).isEqualTo(DISK_PATH);
        assertThat(response.tiers()).singleElement().satisfies(tier -> assertThat(tier.fileNames()).isEqualTo(FILE_NAMES));
    }

    @Test
    void alertsAndRestoreFailureAreExposedAsClosedCodes() {
        BackupStatusResponse response = BackupStatusResponse.of(completeStatus(), BackupStatusViewer.DIRECTOR);

        assertThat(response.alerts()).singleElement().satisfies(alert -> assertThat(alert.code()).isEqualTo(BackupAlertCode.BACKUP_FAILED));
        assertThat(response.lastRestoreTest().failureCode()).isEqualTo(RestoreTestFailureCode.COUNTS_MISMATCH);
    }

    @Test
    void missingDiskAndRestoreTestStayNull() {
        BackupStatus status = new BackupStatus(BackupState.NOT_CONFIGURED, new BackupSummary(null, null, null, 4, 0), null,
                List.of(), null, null, List.of());

        BackupStatusResponse response = BackupStatusResponse.of(status, BackupStatusViewer.PLATFORM_ADMIN);

        assertThat(response.disk()).isNull();
        assertThat(response.lastRestoreTest()).isNull();
    }
}
