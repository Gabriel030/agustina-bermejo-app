package com.red_escuela.backend.shared.permission;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionSnapshotTest {

    private static final Set<String> ALL = Set.of("announcement.create", "billing.refund");
    private static final Set<String> FIXED = Set.of("billing.refund");

    private final PermissionSnapshot snapshot = new PermissionSnapshot(
            Map.of("DOCENTE", Set.of("announcement.create", "billing.refund"), "DIRECTOR", Set.of()),
            Map.of(), FIXED, ALL);

    @Test
    void directorAlwaysHoldsEveryPermissionEvenWithoutGrantRows() {
        assertThat(snapshot.grants("DIRECTOR", "announcement.create")).isTrue();
        assertThat(snapshot.grants("DIRECTOR", "billing.refund")).isTrue();
    }

    @Test
    void gateIgnoresGrantsOfFixedPermissionsToOtherRoles() {
        assertThat(snapshot.grants("DOCENTE", "billing.refund")).isFalse();
        assertThat(snapshot.grants("DOCENTE", "announcement.create")).isTrue();
    }

    @Test
    void unknownPermissionIsDeniedToEveryRole() {
        assertThat(snapshot.grants("DIRECTOR", "unknown.code")).isFalse();
        assertThat(snapshot.grants("DOCENTE", "unknown.code")).isFalse();
    }

    @Test
    void moduleWithoutExplicitModeIsLegacy() {
        assertThat(snapshot.modeOf("DOCENTE", "announcement")).isEqualTo(PermissionMode.LEGACY);
    }
}
