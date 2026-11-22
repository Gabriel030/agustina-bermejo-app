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

@SlowTest(reason = "recorre los endpoints de platform por rol contra el contexto completo")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PlatformPermissionComparisonIT extends AuthorizationMatrixSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PermissionSnapshotCache cache;

    @AfterEach
    void restoreDefaultModes() {
        jdbcTemplate.update("delete from permission_module_mode where module = 'platform'");
        jdbcTemplate.update("delete from role_permission where role = 'PRECEPTOR' and permission_id in (select id from permission where module = 'platform')");
        cache.invalidate();
    }

    private List<AuthorizationMatrixRow> platformRows() {
        Map<String, MappedEndpoint> endpoints = endpointsByKey();
        return AuthorizationMatrixFile.read().stream()
                .filter(row -> endpoints.containsKey(row.key()))
                .filter(row -> endpoints.get(row.key()).handler().hasMethodAnnotation(RequiresPermission.class))
                .filter(row -> endpoints.get(row.key()).handler().getMethodAnnotation(RequiresPermission.class).code().startsWith("platform."))
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
            jdbcTemplate.update("insert into permission_module_mode (role, module, mode) values (?, ?, ?)", role, "platform", mode);
        }
        cache.invalidate();
    }

    private void assertDecisionsMatchLegacy(String mode) throws Exception {
        List<AuthorizationMatrixRow> rows = platformRows();
        Map<String, MappedEndpoint> endpoints = endpointsByKey();
        long gatedPlatformEndpoints = endpoints.values().stream()
                .filter(endpoint -> endpoint.handler().hasMethodAnnotation(RequiresPermission.class))
                .filter(endpoint -> endpoint.handler().getMethodAnnotation(RequiresPermission.class).code().startsWith("platform."))
                .count();
        assertThat(gatedPlatformEndpoints).isPositive();
        assertThat(rows).hasSize((int) gatedPlatformEndpoints);
        List<String> mismatches = new ArrayList<>();
        for (AuthorizationMatrixRow row : rows) {
            assertThat(endpoints.get(row.key())).as(row.key()).isNotNull();
            for (MatrixRole role : MatrixRole.values()) {
                boolean expectedDenied = row.byRole().get(role) == AccessExpectation.DENY;
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
    void legacyModeMatchesTheOldRulesForEveryPlatformEndpointAndRole() throws Exception {
        assertDecisionsMatchLegacy("LEGACY");
    }

    @Test
    void shadowModeMatchesTheOldRulesForEveryPlatformEndpointAndRole() throws Exception {
        setModuleMode("SHADOW");
        assertDecisionsMatchLegacy("SHADOW");
    }

    @Test
    void enforceModeWithTheDefaultGridMatchesTheOldRulesForEveryPlatformEndpointAndRole() throws Exception {
        setModuleMode("ENFORCE");
        assertDecisionsMatchLegacy("ENFORCE");
    }

    @Test
    void legacyModeIgnoresTheGridGrantedToAnotherRole() throws Exception {
        jdbcTemplate.update("insert into role_permission (role, permission_id) select 'PRECEPTOR', id from permission where module = 'platform'");
        cache.invalidate();
        assertDecisionsMatchLegacy("LEGACY");
    }

    @Test
    void enforceModeFollowsTheGridWhenAPermissionIsGrantedToAnotherRole() throws Exception {
        setModuleMode("ENFORCE");
        Map<String, MappedEndpoint> endpoints = endpointsByKey();
        AuthorizationMatrixRow listRow = platformRows().stream().filter(row -> row.key().equals("GET /institute-settings")).findFirst().orElseThrow();
        assertThat(isForbiddenByTheGate(endpoints, listRow, MatrixRole.PRECEPTOR)).isTrue();
        jdbcTemplate.update("insert into role_permission (role, permission_id) select 'PRECEPTOR', id from permission where code = 'platform.read_institute_settings'");
        cache.invalidate();
        assertThat(isForbiddenByTheGate(endpoints, listRow, MatrixRole.PRECEPTOR)).isFalse();
    }
}
