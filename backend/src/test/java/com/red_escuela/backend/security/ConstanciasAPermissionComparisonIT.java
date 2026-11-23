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

@SlowTest(reason = "recorre los endpoints de configuración de constancias migrados por rol contra el contexto completo")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ConstanciasAPermissionComparisonIT extends AuthorizationMatrixSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PermissionSnapshotCache cache;

    @AfterEach
    void restoreDefaultModes() {
        jdbcTemplate.update("delete from permission_module_mode where module = 'certificate'");
        jdbcTemplate.update("delete from role_permission where role = 'ALUMNO' and permission_id in (select id from permission where module = 'certificate')");
        jdbcTemplate.update("delete from role_permission where role = 'ADMINISTRATIVO' and permission_id in (select id from permission where module = 'certificate')");
        jdbcTemplate.update("insert into role_permission (role, permission_id) select 'ADMINISTRATIVO', id from permission where code in ('certificate.issue', 'certificate.download', 'certificate.templates_view', 'certificate.signers_view', 'certificate.document_settings_view', 'certificate.regular_settings_view')");
        cache.invalidate();
    }

    private static final List<String> MIGRATED_CONTROLLERS = List.of("DocumentCertificateTemplateController", "CertificateSignatoryController", "DocumentIssuanceSettingsController", "RegularCertificateSettingsController");

    private static boolean isMigrated(MappedEndpoint endpoint) {
        return MIGRATED_CONTROLLERS.contains(endpoint.handler().getBeanType().getSimpleName())
                && !"preview".equals(endpoint.handler().getMethod().getName());
    }

    private List<AuthorizationMatrixRow> constanciasRows() {
        Map<String, MappedEndpoint> endpoints = endpointsByKey();
        return AuthorizationMatrixFile.read().stream()
                .filter(row -> endpoints.containsKey(row.key()))
                .filter(row -> isMigrated(endpoints.get(row.key())))
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
        for (String module : List.of("certificate")) {
            for (String role : List.of("ALUMNO", "DOCENTE", "PRECEPTOR", "ADMINISTRATIVO", "DIRECTOR", "PLATFORM_ADMIN")) {
                jdbcTemplate.update("insert into permission_module_mode (role, module, mode) values (?, ?, ?)", role, module, mode);
            }
        }
        cache.invalidate();
    }

    private void assertDecisionsMatchLegacy(String mode) throws Exception {
        List<AuthorizationMatrixRow> rows = constanciasRows();
        Map<String, MappedEndpoint> endpoints = endpointsByKey();
        assertThat(rows).hasSize(21);
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
    void legacyModeMatchesTheOldRulesForEveryConstanciasConfigurationEndpointAndRole() throws Exception {
        assertDecisionsMatchLegacy("LEGACY");
    }

    @Test
    void shadowModeMatchesTheOldRulesForEveryConstanciasConfigurationEndpointAndRole() throws Exception {
        setModuleMode("SHADOW");
        assertDecisionsMatchLegacy("SHADOW");
    }

    @Test
    void enforceModeWithTheDefaultGridMatchesTheOldRulesForEveryConstanciasConfigurationEndpointAndRole() throws Exception {
        setModuleMode("ENFORCE");
        assertDecisionsMatchLegacy("ENFORCE");
    }

    @Test
    void legacyModeIgnoresTheGridGrantedToAnotherRole() throws Exception {
        jdbcTemplate.update("insert into role_permission (role, permission_id) select 'ALUMNO', id from permission where module = 'certificate'");
        cache.invalidate();
        assertDecisionsMatchLegacy("LEGACY");
    }

    @Test
    void enforceModeFollowsTheGridWhenAPermissionIsGrantedToAnotherRole() throws Exception {
        setModuleMode("ENFORCE");
        Map<String, MappedEndpoint> endpoints = endpointsByKey();
        AuthorizationMatrixRow signatoriesRow = constanciasRows().stream().filter(row -> row.key().equals("GET /certificate-signatories")).findFirst().orElseThrow();
        AuthorizationMatrixRow settingsRow = constanciasRows().stream().filter(row -> row.key().equals("PUT /certificate-settings/documents")).findFirst().orElseThrow();
        assertThat(isForbiddenByTheGate(endpoints, signatoriesRow, MatrixRole.ALUMNO)).isTrue();
        assertThat(isForbiddenByTheGate(endpoints, settingsRow, MatrixRole.ADMINISTRATIVO)).isTrue();
        jdbcTemplate.update("insert into role_permission (role, permission_id) select 'ALUMNO', id from permission where code = 'certificate.signers_view'");
        jdbcTemplate.update("insert into role_permission (role, permission_id) select 'ADMINISTRATIVO', id from permission where code = 'certificate.document_settings_manage'");
        cache.invalidate();
        assertThat(isForbiddenByTheGate(endpoints, signatoriesRow, MatrixRole.ALUMNO)).isFalse();
        assertThat(isForbiddenByTheGate(endpoints, settingsRow, MatrixRole.ADMINISTRATIVO)).isFalse();
    }

    @Test
    void enforceModeDeniesTheAdministrativeRoleWhenItsGrantIsRemoved() throws Exception {
        setModuleMode("ENFORCE");
        Map<String, MappedEndpoint> endpoints = endpointsByKey();
        AuthorizationMatrixRow signatoriesRow = constanciasRows().stream().filter(row -> row.key().equals("GET /certificate-signatories")).findFirst().orElseThrow();
        assertThat(isForbiddenByTheGate(endpoints, signatoriesRow, MatrixRole.ADMINISTRATIVO)).isFalse();
        jdbcTemplate.update("delete from role_permission where role = 'ADMINISTRATIVO' and permission_id = (select id from permission where code = 'certificate.signers_view')");
        cache.invalidate();
        assertThat(isForbiddenByTheGate(endpoints, signatoriesRow, MatrixRole.ADMINISTRATIVO)).isTrue();
    }
}
