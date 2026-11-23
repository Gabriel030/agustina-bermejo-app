package com.red_escuela.backend.security;

import com.red_escuela.backend.shared.permission.PermissionSnapshotCache;
import com.red_escuela.backend.shared.permission.RequiresPermission;
import com.red_escuela.backend.testsupport.SlowTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SlowTest(reason = "recorre los endpoints de notification por rol contra el contexto completo")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class NotificationPermissionComparisonIT extends AuthorizationMatrixSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PermissionSnapshotCache cache;

    @AfterEach
    void restoreDefaultModes() {
        jdbcTemplate.update("delete from permission_module_mode where module = 'notification'");
        jdbcTemplate.update("delete from role_permission where role = 'ALUMNO' and permission_id in (select id from permission where module = 'notification')");
        cache.invalidate();
    }

    private List<AuthorizationMatrixRow> notificationRows() {
        Map<String, MappedEndpoint> endpoints = endpointsByKey();
        return AuthorizationMatrixFile.read().stream()
                .filter(row -> endpoints.containsKey(row.key()))
                .filter(row -> endpoints.get(row.key()).handler().getMethodAnnotation(RequiresPermission.class) != null)
                .filter(row -> endpoints.get(row.key()).handler().getBeanType().getPackageName().startsWith("com.red_escuela.backend.notification"))
                .filter(row -> !endpoints.get(row.key()).handler().getBeanType().getPackageName().startsWith("com.red_escuela.backend.notification.invitation"))
                .toList();
    }

    private Map<String, MappedEndpoint> endpointsByKey() {
        return mappedEndpoints().stream().collect(Collectors.toMap(MappedEndpoint::key, Function.identity(), (first, second) -> first));
    }

    private boolean isForbiddenByTheGate(Map<String, MappedEndpoint> endpoints, AuthorizationMatrixRow row, MatrixRole role) throws Exception {
        int status = statusOf(endpoints.get(row.key()), row.body() == null ? "" : row.body(), role);
        return status == 403 || (role == null && status == 401);
    }

    private void setModuleMode(String mode) {
        for (String role : List.of("ALUMNO", "DOCENTE", "PRECEPTOR", "ADMINISTRATIVO", "DIRECTOR", "PLATFORM_ADMIN")) {
            jdbcTemplate.update("insert into permission_module_mode (role, module, mode) values (?, ?, ?)", role, "notification", mode);
        }
        cache.invalidate();
    }

    private void assertDecisionsMatchLegacy(String mode) throws Exception {
        List<AuthorizationMatrixRow> rows = notificationRows();
        Map<String, MappedEndpoint> endpoints = endpointsByKey();
        assertThat(rows).hasSize(18);
        List<String> mismatches = new ArrayList<>();
        for (AuthorizationMatrixRow row : rows) {
            assertThat(endpoints.get(row.key())).as(row.key()).isNotNull();
            for (MatrixRole role : MatrixRole.values()) {
                boolean expectedDenied = row.byRole().get(role) == AccessExpectation.DENY;
                if ("ENFORCE".equals(mode) && role == MatrixRole.DIRECTOR) {
                    expectedDenied = false;
                }
                if (isForbiddenByTheGate(endpoints, row, role) != expectedDenied) {
                    mismatches.add(mode + " " + role + " " + row.key() + " expected denied=" + expectedDenied);
                }
            }
            if (!isForbiddenByTheGate(endpoints, row, null)) {
                mismatches.add(mode + " ANONYMOUS " + row.key() + " must be denied");
            }
        }
        assertThat(mismatches).isEmpty();
    }

    @Test
    void legacyModeMatchesTheOldRulesForEveryNotificationEndpointAndRole() throws Exception {
        assertDecisionsMatchLegacy("LEGACY");
    }

    @Test
    void shadowModeMatchesTheOldRulesForEveryNotificationEndpointAndRole() throws Exception {
        setModuleMode("SHADOW");
        assertDecisionsMatchLegacy("SHADOW");
    }

    @Test
    void enforceModeWithTheDefaultGridMatchesTheOldRulesForEveryNotificationEndpointAndRole() throws Exception {
        setModuleMode("ENFORCE");
        assertDecisionsMatchLegacy("ENFORCE");
    }

    @Test
    void legacyModeIgnoresTheGridGrantedToAnotherRole() throws Exception {
        jdbcTemplate.update("insert into role_permission (role, permission_id) select 'ALUMNO', id from permission where module = 'notification'");
        cache.invalidate();
        assertDecisionsMatchLegacy("LEGACY");
    }

    @Test
    void enforceModeFollowsTheGridWhenAPermissionIsGrantedToAnotherRole() throws Exception {
        setModuleMode("ENFORCE");
        Map<String, MappedEndpoint> endpoints = endpointsByKey();
        AuthorizationMatrixRow listRow = notificationRows().stream().filter(row -> row.key().equals("GET /broadcasts")).findFirst().orElseThrow();
        assertThat(isForbiddenByTheGate(endpoints, listRow, MatrixRole.ALUMNO)).isTrue();
        jdbcTemplate.update("insert into role_permission (role, permission_id) select 'ALUMNO', id from permission where code = 'notification.read_broadcasts'");
        cache.invalidate();
        assertThat(isForbiddenByTheGate(endpoints, listRow, MatrixRole.ALUMNO)).isFalse();
    }

    @Test
    void enforceModeLetsADelegatedRoleReadTheCollectionReminderMetrics() throws Exception {
        setModuleMode("ENFORCE");
        Map<String, MappedEndpoint> endpoints = endpointsByKey();
        AuthorizationMatrixRow metricsRow = notificationRows().stream().filter(row -> row.key().equals("GET /notification-metrics/collection-reminders")).findFirst().orElseThrow();
        assertThat(isForbiddenByTheGate(endpoints, metricsRow, MatrixRole.ALUMNO)).isTrue();
        jdbcTemplate.update("insert into role_permission (role, permission_id) select 'ALUMNO', id from permission where code = 'notification.read_collection_reminder_metrics'");
        cache.invalidate();
        assertThat(isForbiddenByTheGate(endpoints, metricsRow, MatrixRole.ALUMNO)).isFalse();
    }
}
