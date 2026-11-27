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

@SlowTest(reason = "recorre los endpoints de evaluation por rol contra el contexto completo")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EvaluationPermissionComparisonIT extends AuthorizationMatrixSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PermissionSnapshotCache cache;

    @AfterEach
    void restoreDefaultModes() {
        jdbcTemplate.update("delete from permission_module_mode where module = 'evaluation'");
        jdbcTemplate.update("delete from role_permission where role = 'ALUMNO' and permission_id in (select id from permission where module = 'evaluation')");
        jdbcTemplate.update("delete from role_permission where role = 'PRECEPTOR' and permission_id in (select id from permission where code = 'evaluation.approve_commission_closure')");
        cache.invalidate();
    }

    private List<AuthorizationMatrixRow> evaluationRows() {
        Map<String, MappedEndpoint> endpoints = endpointsByKey();
        return AuthorizationMatrixFile.read().stream()
                .filter(row -> endpoints.containsKey(row.key()))
                .filter(row -> endpoints.get(row.key()).handler().getMethodAnnotation(RequiresPermission.class) != null)
                .filter(row -> endpoints.get(row.key()).handler().getBeanType().getPackageName().startsWith("com.red_escuela.backend.evaluation"))
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
            jdbcTemplate.update("insert into permission_module_mode (role, module, mode) values (?, ?, ?)", role, "evaluation", mode);
        }
        cache.invalidate();
    }

    private void assertDecisionsMatchLegacy(String mode) throws Exception {
        List<AuthorizationMatrixRow> rows = evaluationRows();
        Map<String, MappedEndpoint> endpoints = endpointsByKey();
        assertThat(rows).hasSize(54);
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
    void legacyModeMatchesTheOldRulesForEveryEvaluationEndpointAndRole() throws Exception {
        assertDecisionsMatchLegacy("LEGACY");
    }

    @Test
    void shadowModeMatchesTheOldRulesForEveryEvaluationEndpointAndRole() throws Exception {
        setModuleMode("SHADOW");
        assertDecisionsMatchLegacy("SHADOW");
    }

    @Test
    void enforceModeWithTheDefaultGridMatchesTheOldRulesForEveryEvaluationEndpointAndRole() throws Exception {
        setModuleMode("ENFORCE");
        assertDecisionsMatchLegacy("ENFORCE");
    }

    @Test
    void legacyModeIgnoresTheGridGrantedToAnotherRole() throws Exception {
        jdbcTemplate.update("insert into role_permission (role, permission_id) select 'ALUMNO', id from permission where module = 'evaluation'");
        cache.invalidate();
        assertDecisionsMatchLegacy("LEGACY");
    }

    @Test
    void enforceModeFollowsTheGridWhenAPermissionIsGrantedToAnotherRole() throws Exception {
        setModuleMode("ENFORCE");
        Map<String, MappedEndpoint> endpoints = endpointsByKey();
        AuthorizationMatrixRow schemesRow = evaluationRows().stream().filter(row -> row.key().equals("GET /evaluation-schemes")).findFirst().orElseThrow();
        assertThat(isForbiddenByTheGate(endpoints, schemesRow, MatrixRole.ALUMNO)).isTrue();
        jdbcTemplate.update("insert into role_permission (role, permission_id) select 'ALUMNO', id from permission where code = 'evaluation.read_evaluation_settings'");
        cache.invalidate();
        assertThat(isForbiddenByTheGate(endpoints, schemesRow, MatrixRole.ALUMNO)).isFalse();
    }

    @Test
    void enforceModeLetsADelegatedRoleApproveACommissionClosure() throws Exception {
        setModuleMode("ENFORCE");
        Map<String, MappedEndpoint> endpoints = endpointsByKey();
        AuthorizationMatrixRow approveRow = evaluationRows().stream().filter(row -> row.key().equals("POST /course-sections/{id}/closure/approve")).findFirst().orElseThrow();
        assertThat(isForbiddenByTheGate(endpoints, approveRow, MatrixRole.PRECEPTOR)).isTrue();
        jdbcTemplate.update("insert into role_permission (role, permission_id) select 'PRECEPTOR', id from permission where code = 'evaluation.approve_commission_closure'");
        cache.invalidate();
        assertThat(isForbiddenByTheGate(endpoints, approveRow, MatrixRole.PRECEPTOR)).isFalse();
    }

    @Test
    void enforceModeLetsTheDirectorThroughTheEndpointsThatLegacyDeniesHim() throws Exception {
        setModuleMode("ENFORCE");
        Map<String, MappedEndpoint> endpoints = endpointsByKey();
        List<String> deniedToTheDirectorInLegacy = evaluationRows().stream()
                .filter(row -> row.byRole().get(MatrixRole.DIRECTOR) == AccessExpectation.DENY)
                .map(AuthorizationMatrixRow::key)
                .toList();
        assertThat(deniedToTheDirectorInLegacy).contains("POST /exam-boards", "POST /exam-boards/{id}/cancel", "POST /exam-boards/{id}/close", "PUT /exam-boards/{id}/final-grades", "POST /program-sections/{id}/closure/approve", "POST /grade-items/{id}/recovery");
        for (AuthorizationMatrixRow row : evaluationRows()) {
            if (deniedToTheDirectorInLegacy.contains(row.key())) {
                assertThat(isForbiddenByTheGate(endpoints, row, MatrixRole.DIRECTOR)).as(row.key()).isFalse();
            }
        }
    }
}
