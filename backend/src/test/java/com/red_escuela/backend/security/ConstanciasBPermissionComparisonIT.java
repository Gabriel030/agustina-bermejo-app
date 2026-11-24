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

@SlowTest(reason = "recorre los endpoints de emisión y excepciones de constancias migrados por rol contra el contexto completo")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ConstanciasBPermissionComparisonIT extends AuthorizationMatrixSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PermissionSnapshotCache cache;

    @AfterEach
    void restoreDefaultModes() {
        jdbcTemplate.update("delete from permission_module_mode where module = 'certificate'");
        restoreCertificateDefaultGrants(jdbcTemplate);
        cache.invalidate();
    }

    static void restoreCertificateDefaultGrants(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update("delete from role_permission where role in ('ALUMNO', 'DOCENTE', 'ADMINISTRATIVO') and permission_id in (select id from permission where module = 'certificate')");
        jdbcTemplate.update("insert into role_permission (role, permission_id) select 'ADMINISTRATIVO', id from permission where code in ('certificate.issue', 'certificate.download', 'certificate.templates_view', 'certificate.signers_view', 'certificate.document_settings_view', 'certificate.regular_settings_view', 'certificate.exceptions_view', 'certificate.exceptions_request', 'certificate.exceptions_withdraw', 'certificate.regular_view', 'certificate.regular_requests_view', 'certificate.regular_requests_reject', 'certificate.exam_candidates_view', 'certificate.exam_download', 'certificate.exam_issue_batch')");
        jdbcTemplate.update("insert into role_permission (role, permission_id) select 'DOCENTE', id from permission where code in ('certificate.exam_candidates_view', 'certificate.exam_download', 'certificate.exam_issue_batch')");
    }

    private static final List<String> MIGRATED_CONTROLLERS = List.of("CertificateExceptionController", "RegularCertificateController", "RegularCertificateRequestController", "ExamBoardCertificateController", "ExamCertificateController", "ApprovedSubjectsCertificateController");

    private static boolean isMigrated(MappedEndpoint endpoint) {
        return MIGRATED_CONTROLLERS.contains(endpoint.handler().getBeanType().getSimpleName());
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
    void legacyModeMatchesTheOldRulesForEveryConstanciasIssuanceEndpointAndRole() throws Exception {
        assertDecisionsMatchLegacy("LEGACY");
    }

    @Test
    void shadowModeMatchesTheOldRulesForEveryConstanciasIssuanceEndpointAndRole() throws Exception {
        setModuleMode("SHADOW");
        assertDecisionsMatchLegacy("SHADOW");
    }

    @Test
    void enforceModeWithTheDefaultGridMatchesTheOldRulesForEveryConstanciasIssuanceEndpointAndRole() throws Exception {
        setModuleMode("ENFORCE");
        assertDecisionsMatchLegacy("ENFORCE");
    }

    @Test
    void legacyModeIgnoresTheGridGrantedToAnotherRole() throws Exception {
        jdbcTemplate.update("insert into role_permission (role, permission_id) select 'ALUMNO', id from permission where module = 'certificate'");
        cache.invalidate();
        assertDecisionsMatchLegacy("LEGACY");
    }

    private AuthorizationMatrixRow rowOf(String key) {
        return constanciasRows().stream().filter(row -> row.key().equals(key)).findFirst().orElseThrow();
    }

    @Test
    void enforceModeFollowsTheGridWhenAPermissionIsGrantedToAnotherRole() throws Exception {
        setModuleMode("ENFORCE");
        Map<String, MappedEndpoint> endpoints = endpointsByKey();
        AuthorizationMatrixRow approveRow = rowOf("POST /certificate-exceptions/{id}/approve");
        AuthorizationMatrixRow revokeRow = rowOf("POST /regular-certificates/{id}/revoke");
        assertThat(isForbiddenByTheGate(endpoints, approveRow, MatrixRole.ADMINISTRATIVO)).isTrue();
        assertThat(isForbiddenByTheGate(endpoints, revokeRow, MatrixRole.ALUMNO)).isTrue();
        jdbcTemplate.update("insert into role_permission (role, permission_id) select 'ADMINISTRATIVO', id from permission where code = 'certificate.exceptions_approve'");
        jdbcTemplate.update("insert into role_permission (role, permission_id) select 'ALUMNO', id from permission where code = 'certificate.revoke'");
        cache.invalidate();
        assertThat(isForbiddenByTheGate(endpoints, approveRow, MatrixRole.ADMINISTRATIVO)).isFalse();
        assertThat(isForbiddenByTheGate(endpoints, revokeRow, MatrixRole.ALUMNO)).isFalse();
    }

    @Test
    void enforceModeDeniesTeachersAndAdministrativesWhenTheirExamGrantIsRemoved() throws Exception {
        setModuleMode("ENFORCE");
        Map<String, MappedEndpoint> endpoints = endpointsByKey();
        AuthorizationMatrixRow batchRow = rowOf("POST /exam-boards/{id}/exam-certificates/batch");
        assertThat(isForbiddenByTheGate(endpoints, batchRow, MatrixRole.DOCENTE)).isFalse();
        assertThat(isForbiddenByTheGate(endpoints, batchRow, MatrixRole.ADMINISTRATIVO)).isFalse();
        jdbcTemplate.update("delete from role_permission where role in ('DOCENTE', 'ADMINISTRATIVO') and permission_id = (select id from permission where code = 'certificate.exam_issue_batch')");
        cache.invalidate();
        assertThat(isForbiddenByTheGate(endpoints, batchRow, MatrixRole.DOCENTE)).isTrue();
        assertThat(isForbiddenByTheGate(endpoints, batchRow, MatrixRole.ADMINISTRATIVO)).isTrue();
    }
}
