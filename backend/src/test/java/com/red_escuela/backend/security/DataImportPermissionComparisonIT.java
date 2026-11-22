package com.red_escuela.backend.security;

import com.red_escuela.backend.shared.permission.PermissionSnapshotCache;
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

@SlowTest(reason = "recorre los endpoints de dataimport por rol contra el contexto completo")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DataImportPermissionComparisonIT extends AuthorizationMatrixSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PermissionSnapshotCache cache;

    @AfterEach
    void restoreDefaultModes() {
        jdbcTemplate.update("delete from permission_module_mode where module = 'dataimport'");
        jdbcTemplate.update("delete from role_permission where role = 'ADMINISTRATIVO' and permission_id in (select id from permission where module = 'dataimport')");
        cache.invalidate();
    }

    private List<AuthorizationMatrixRow> dataImportRows() {
        return AuthorizationMatrixFile.read().stream().filter(row -> row.path().startsWith("/data-imports")).toList();
    }

    private Map<String, MappedEndpoint> endpointsByKey() {
        return mappedEndpoints().stream().collect(Collectors.toMap(MappedEndpoint::key, Function.identity(), (first, second) -> first));
    }

    private boolean isForbiddenByTheGate(AuthorizationMatrixRow row, MatrixRole role) throws Exception {
        int status = statusOf(endpointsByKey().get(row.key()), row.body() == null ? "" : row.body(), role);
        return status == 403 || (role == null && status == 401);
    }

    private void setModuleMode(String mode) {
        for (String role : List.of("ALUMNO", "DOCENTE", "PRECEPTOR", "ADMINISTRATIVO", "DIRECTOR", "PLATFORM_ADMIN")) {
            jdbcTemplate.update("insert into permission_module_mode (role, module, mode) values (?, ?, ?)", role, "dataimport", mode);
        }
        cache.invalidate();
    }

    private void assertDecisionsMatchLegacy(String mode) throws Exception {
        List<AuthorizationMatrixRow> rows = dataImportRows();
        assertThat(rows).hasSize(13);
        List<String> mismatches = new ArrayList<>();
        for (AuthorizationMatrixRow row : rows) {
            assertThat(endpointsByKey().get(row.key())).as(row.key()).isNotNull();
            for (MatrixRole role : MatrixRole.values()) {
                boolean expectedDenied = row.byRole().get(role) == AccessExpectation.DENY;
                if (isForbiddenByTheGate(row, role) != expectedDenied) {
                    mismatches.add(mode + " " + role + " " + row.key() + " expected denied=" + expectedDenied);
                }
            }
            if (!isForbiddenByTheGate(row, null)) {
                mismatches.add(mode + " ANONYMOUS " + row.key() + " must be denied");
            }
        }
        assertThat(mismatches).isEmpty();
    }

    @Test
    void legacyModeMatchesTheOldRulesForEveryDataImportEndpointAndRole() throws Exception {
        assertDecisionsMatchLegacy("LEGACY");
    }

    @Test
    void shadowModeMatchesTheOldRulesForEveryDataImportEndpointAndRole() throws Exception {
        setModuleMode("SHADOW");
        assertDecisionsMatchLegacy("SHADOW");
    }

    @Test
    void enforceModeWithTheDefaultGridMatchesTheOldRulesForEveryDataImportEndpointAndRole() throws Exception {
        setModuleMode("ENFORCE");
        assertDecisionsMatchLegacy("ENFORCE");
    }

    @Test
    void legacyModeIgnoresTheGridGrantedToAnotherRole() throws Exception {
        jdbcTemplate.update("insert into role_permission (role, permission_id) select 'ADMINISTRATIVO', id from permission where module = 'dataimport'");
        cache.invalidate();
        assertDecisionsMatchLegacy("LEGACY");
    }

    @Test
    void enforceModeFollowsTheGridWhenAPermissionIsGrantedToAnotherRole() throws Exception {
        setModuleMode("ENFORCE");
        AuthorizationMatrixRow listRow = dataImportRows().stream().filter(row -> row.key().equals("GET /data-imports")).findFirst().orElseThrow();
        assertThat(isForbiddenByTheGate(listRow, MatrixRole.ADMINISTRATIVO)).isTrue();
        jdbcTemplate.update("insert into role_permission (role, permission_id) select 'ADMINISTRATIVO', id from permission where code = 'dataimport.read'");
        cache.invalidate();
        assertThat(isForbiddenByTheGate(listRow, MatrixRole.ADMINISTRATIVO)).isFalse();
    }
}
