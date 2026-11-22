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
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SlowTest(reason = "recorre los endpoints de billing de la tanda A1 por rol contra el contexto completo")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class BillingReadsA1PermissionComparisonIT extends AuthorizationMatrixSupport {

    private static final Set<String> A1_CODES = Set.of(
            "billing.read_billing_metrics", "billing.read_financial_dashboard", "billing.read_debtors",
            "billing.read_student_billing_overview", "billing.read_payment_standing", "billing.read_payment_standing_history",
            "billing.read_overdue_contacts", "billing.preview_reminder_impact", "billing.read_reminder_config",
            "billing.manage_reminder_config", "billing.read_billing_runs", "billing.simulate_monthly_run", "billing.read_late_payment_settings");

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PermissionSnapshotCache cache;

    @AfterEach
    void restoreDefaultModes() {
        jdbcTemplate.update("delete from permission_module_mode where module = 'billing'");
        jdbcTemplate.update("delete from role_permission where role = 'ALUMNO' and permission_id in (select id from permission where module = 'billing')");
        jdbcTemplate.update("delete from role_permission where role = 'PRECEPTOR' and permission_id in (select id from permission where module = 'billing' and code <> 'billing.read_payment_standing')");
        jdbcTemplate.update("delete from role_permission where role = 'ADMINISTRATIVO' and permission_id in (select id from permission where code = 'billing.read_debtors')");
        cache.invalidate();
    }

    private List<AuthorizationMatrixRow> billingRows() {
        Map<String, MappedEndpoint> endpoints = endpointsByKey();
        return AuthorizationMatrixFile.read().stream()
                .filter(row -> endpoints.containsKey(row.key()))
                .filter(row -> {
                    RequiresPermission annotation = endpoints.get(row.key()).handler().getMethodAnnotation(RequiresPermission.class);
                    return annotation != null && A1_CODES.contains(annotation.code());
                })
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
            jdbcTemplate.update("insert into permission_module_mode (role, module, mode) values (?, ?, ?)", role, "billing", mode);
        }
        cache.invalidate();
    }

    private AuthorizationMatrixRow rowOf(String key) {
        return billingRows().stream().filter(row -> row.key().equals(key)).findFirst().orElseThrow();
    }

    private void grantToRole(String role, String code) {
        jdbcTemplate.update("insert into role_permission (role, permission_id) select ?, id from permission where code = ?", role, code);
        cache.invalidate();
    }

    private void assertDecisionsMatchLegacy(String mode) throws Exception {
        List<AuthorizationMatrixRow> rows = billingRows();
        Map<String, MappedEndpoint> endpoints = endpointsByKey();
        assertThat(rows).hasSize(33);
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
    void legacyModeMatchesTheOldRulesForEveryBillingReadEndpointAndRole() throws Exception {
        assertDecisionsMatchLegacy("LEGACY");
    }

    @Test
    void shadowModeMatchesTheOldRulesForEveryBillingReadEndpointAndRole() throws Exception {
        setModuleMode("SHADOW");
        assertDecisionsMatchLegacy("SHADOW");
    }

    @Test
    void enforceModeWithTheDefaultGridMatchesTheOldRulesForEveryBillingReadEndpointAndRole() throws Exception {
        setModuleMode("ENFORCE");
        assertDecisionsMatchLegacy("ENFORCE");
    }

    @Test
    void legacyModeIgnoresTheGridGrantedToAnotherRole() throws Exception {
        jdbcTemplate.update("insert into role_permission (role, permission_id) select 'ALUMNO', id from permission where module = 'billing'");
        cache.invalidate();
        assertDecisionsMatchLegacy("LEGACY");
    }

    @Test
    void enforceModeFollowsTheGridWhenAPermissionIsGrantedToAnotherRole() throws Exception {
        setModuleMode("ENFORCE");
        Map<String, MappedEndpoint> endpoints = endpointsByKey();
        AuthorizationMatrixRow runsRow = rowOf("GET /billing-runs");
        assertThat(isForbiddenByTheGate(endpoints, runsRow, MatrixRole.PRECEPTOR)).isTrue();
        grantToRole("PRECEPTOR", "billing.read_billing_runs");
        assertThat(isForbiddenByTheGate(endpoints, runsRow, MatrixRole.PRECEPTOR)).isFalse();
    }

    @Test
    void enforceModeLetsADelegatedRoleReachTheReminderConfigurationServicesWithoutAServiceLevelRule() throws Exception {
        setModuleMode("ENFORCE");
        Map<String, MappedEndpoint> endpoints = endpointsByKey();
        AuthorizationMatrixRow versionsRow = rowOf("GET /billing-configuration/COLLECTION_REMINDERS/topics/COLLECTION_REMINDERS/versions");
        AuthorizationMatrixRow policiesRow = rowOf("GET /billing-configuration/collection-reminder-schedules");
        assertThat(isForbiddenByTheGate(endpoints, versionsRow, MatrixRole.PRECEPTOR)).isTrue();
        grantToRole("PRECEPTOR", "billing.read_reminder_config");
        assertThat(isForbiddenByTheGate(endpoints, versionsRow, MatrixRole.PRECEPTOR)).isFalse();
        assertThat(isForbiddenByTheGate(endpoints, policiesRow, MatrixRole.PRECEPTOR)).isFalse();
    }

    @Test
    void enforceModeLetsADelegatedRoleReachTheMetricsAndStandingServices() throws Exception {
        setModuleMode("ENFORCE");
        Map<String, MappedEndpoint> endpoints = endpointsByKey();
        AuthorizationMatrixRow firstMonthRow = rowOf("GET /billing/metrics/first-month");
        AuthorizationMatrixRow historyRow = rowOf("GET /program-enrollments/{id}/payment-standing/history");
        assertThat(isForbiddenByTheGate(endpoints, firstMonthRow, MatrixRole.PRECEPTOR)).isTrue();
        assertThat(isForbiddenByTheGate(endpoints, historyRow, MatrixRole.PRECEPTOR)).isTrue();
        grantToRole("PRECEPTOR", "billing.read_billing_metrics");
        grantToRole("PRECEPTOR", "billing.read_payment_standing_history");
        assertThat(isForbiddenByTheGate(endpoints, firstMonthRow, MatrixRole.PRECEPTOR)).isFalse();
        assertThat(isForbiddenByTheGate(endpoints, historyRow, MatrixRole.PRECEPTOR)).isFalse();
    }

    @Test
    void enforceModeKeepsTheDebtorsPageFixedToTheGridAndNotToTheOldDirectorOnlyRule() throws Exception {
        setModuleMode("ENFORCE");
        Map<String, MappedEndpoint> endpoints = endpointsByKey();
        AuthorizationMatrixRow debtorsRow = rowOf("GET /financial-dashboard/debtors/page");
        assertThat(isForbiddenByTheGate(endpoints, debtorsRow, MatrixRole.ADMINISTRATIVO)).isTrue();
        grantToRole("ADMINISTRATIVO", "billing.read_debtors");
        assertThat(isForbiddenByTheGate(endpoints, debtorsRow, MatrixRole.ADMINISTRATIVO)).isFalse();
    }

    @Test
    void theDirectorIsNeverDeniedByLegacyOnTheBillingReadEndpointsOfThisBatch() {
        List<String> deniedToTheDirectorInLegacy = billingRows().stream()
                .filter(row -> row.byRole().get(MatrixRole.DIRECTOR) == AccessExpectation.DENY)
                .map(AuthorizationMatrixRow::key)
                .toList();
        assertThat(deniedToTheDirectorInLegacy).isEmpty();
    }
}
