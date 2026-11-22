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

@SlowTest(reason = "recorre los endpoints de enrollment por rol contra el contexto completo")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EnrollmentPermissionComparisonIT extends AuthorizationMatrixSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PermissionSnapshotCache cache;

    @AfterEach
    void restoreDefaultModes() {
        jdbcTemplate.update("delete from permission_module_mode where module = 'enrollment'");
        jdbcTemplate.update("delete from role_permission where role = 'ALUMNO' and permission_id in (select id from permission where module = 'enrollment')");
        jdbcTemplate.update("delete from role_permission where role = 'PRECEPTOR' and permission_id in (select id from permission where code = 'enrollment.manage_waitlist')");
        jdbcTemplate.update("delete from role_permission where role = 'PRECEPTOR' and permission_id in (select id from permission where code in ('enrollment.create_ad_hoc_invoice', 'enrollment.grant_enrollment_fee_exemption'))");
        cache.invalidate();
    }

    private List<AuthorizationMatrixRow> enrollmentRows() {
        Map<String, MappedEndpoint> endpoints = endpointsByKey();
        return AuthorizationMatrixFile.read().stream()
                .filter(row -> endpoints.containsKey(row.key()))
                .filter(row -> endpoints.get(row.key()).handler().getMethodAnnotation(RequiresPermission.class) != null)
                .filter(row -> endpoints.get(row.key()).handler().getBeanType().getPackageName().startsWith("com.red_escuela.backend.enrollment"))
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
            jdbcTemplate.update("insert into permission_module_mode (role, module, mode) values (?, ?, ?)", role, "enrollment", mode);
        }
        cache.invalidate();
    }

    private void assertDecisionsMatchLegacy(String mode) throws Exception {
        List<AuthorizationMatrixRow> rows = enrollmentRows();
        Map<String, MappedEndpoint> endpoints = endpointsByKey();
        assertThat(rows).hasSize(51);
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
    void legacyModeMatchesTheOldRulesForEveryEnrollmentEndpointAndRole() throws Exception {
        assertDecisionsMatchLegacy("LEGACY");
    }

    @Test
    void shadowModeMatchesTheOldRulesForEveryEnrollmentEndpointAndRole() throws Exception {
        setModuleMode("SHADOW");
        assertDecisionsMatchLegacy("SHADOW");
    }

    @Test
    void enforceModeWithTheDefaultGridMatchesTheOldRulesForEveryEnrollmentEndpointAndRole() throws Exception {
        setModuleMode("ENFORCE");
        assertDecisionsMatchLegacy("ENFORCE");
    }

    @Test
    void legacyModeIgnoresTheGridGrantedToAnotherRole() throws Exception {
        jdbcTemplate.update("insert into role_permission (role, permission_id) select 'ALUMNO', id from permission where module = 'enrollment'");
        cache.invalidate();
        assertDecisionsMatchLegacy("LEGACY");
    }

    @Test
    void enforceModeFollowsTheGridWhenAPermissionIsGrantedToAnotherRole() throws Exception {
        setModuleMode("ENFORCE");
        Map<String, MappedEndpoint> endpoints = endpointsByKey();
        AuthorizationMatrixRow waitlistRow = enrollmentRows().stream().filter(row -> row.key().equals("GET /waitlists")).findFirst().orElseThrow();
        assertThat(isForbiddenByTheGate(endpoints, waitlistRow, MatrixRole.ALUMNO)).isTrue();
        jdbcTemplate.update("insert into role_permission (role, permission_id) select 'ALUMNO', id from permission where code = 'enrollment.read_waitlists'");
        cache.invalidate();
        assertThat(isForbiddenByTheGate(endpoints, waitlistRow, MatrixRole.ALUMNO)).isFalse();
    }

    @Test
    void enforceModeLetsADelegatedRoleRemoveAWaitlistEntry() throws Exception {
        setModuleMode("ENFORCE");
        Map<String, MappedEndpoint> endpoints = endpointsByKey();
        AuthorizationMatrixRow removeRow = enrollmentRows().stream().filter(row -> row.key().equals("POST /waitlists/{kind}/entries/{enrollmentId}/remove")).findFirst().orElseThrow();
        assertThat(isForbiddenByTheGate(endpoints, removeRow, MatrixRole.PRECEPTOR)).isTrue();
        jdbcTemplate.update("insert into role_permission (role, permission_id) select 'PRECEPTOR', id from permission where code = 'enrollment.manage_waitlist'");
        cache.invalidate();
        assertThat(isForbiddenByTheGate(endpoints, removeRow, MatrixRole.PRECEPTOR)).isFalse();
    }

    @Test
    void enforceModeLetsTheDirectorThroughTheEndpointsThatLegacyDeniesHim() throws Exception {
        setModuleMode("ENFORCE");
        Map<String, MappedEndpoint> endpoints = endpointsByKey();
        List<String> deniedToTheDirectorInLegacy = enrollmentRows().stream()
                .filter(row -> row.byRole().get(MatrixRole.DIRECTOR) == AccessExpectation.DENY)
                .map(AuthorizationMatrixRow::key)
                .toList();
        assertThat(deniedToTheDirectorInLegacy).contains("POST /program-enrollments/{id}/advance-billing-year", "POST /program-enrollments/{id}/invoices/ad-hoc", "GET /program-enrollment-applications", "POST /director-approval-requests");
        for (AuthorizationMatrixRow row : enrollmentRows()) {
            if (deniedToTheDirectorInLegacy.contains(row.key())) {
                assertThat(isForbiddenByTheGate(endpoints, row, MatrixRole.DIRECTOR)).as(row.key()).isFalse();
            }
        }
    }

    @Test
    void enforceModeLetsADelegatedRoleIssueAnAdHocInvoiceButNotTheOtherRoles() throws Exception {
        setModuleMode("ENFORCE");
        Map<String, MappedEndpoint> endpoints = endpointsByKey();
        AuthorizationMatrixRow invoiceRow = enrollmentRows().stream().filter(row -> row.key().equals("POST /program-enrollments/{id}/invoices/ad-hoc")).findFirst().orElseThrow();
        assertThat(isForbiddenByTheGate(endpoints, invoiceRow, MatrixRole.PRECEPTOR)).isTrue();
        jdbcTemplate.update("insert into role_permission (role, permission_id) select 'PRECEPTOR', id from permission where code = 'enrollment.create_ad_hoc_invoice'");
        cache.invalidate();
        assertThat(isForbiddenByTheGate(endpoints, invoiceRow, MatrixRole.PRECEPTOR)).isFalse();
        assertThat(isForbiddenByTheGate(endpoints, invoiceRow, MatrixRole.DOCENTE)).isTrue();
        assertThat(isForbiddenByTheGate(endpoints, invoiceRow, MatrixRole.ALUMNO)).isTrue();
    }
}
