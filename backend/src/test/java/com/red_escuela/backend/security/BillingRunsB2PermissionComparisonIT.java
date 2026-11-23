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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SlowTest(reason = "recorre los endpoints monetarios de billing de la tanda B2 por rol contra el contexto completo")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class BillingRunsB2PermissionComparisonIT extends AuthorizationMatrixSupport {

    private static final List<String> ADMINISTRATIVE_CODES = List.of(
            "billing.retry_draft_invoice_items", "billing.approve_draft_invoices", "billing.discard_draft_invoices",
            "billing.manage_discount_definition", "billing.grant_student_discount", "billing.revoke_student_discount",
            "billing.propose_price_change", "billing.manage_year_billing_cohorts", "billing.withdraw_authorization_request",
            "billing.request_advance_enrollment_fee");

    private static final List<String> DIRECTOR_ONLY_CODES = List.of(
            "billing.run_billing", "billing.issue_monthly_invoices_manually", "billing.manage_promotion", "billing.cancel_price_version",
            "billing.manage_payment_plan_options", "billing.manage_year_billing_template", "billing.set_late_payment_settings",
            "billing.manage_payment_gateway", "billing.decide_authorization_request", "billing.publish_annual_upfront_terms");

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PermissionSnapshotCache cache;

    @AfterEach
    void restoreDefaultModesAndSeededGrants() {
        jdbcTemplate.update("delete from permission_module_mode where module = 'billing'");
        jdbcTemplate.update("delete from role_permission where permission_id in (select id from permission where code in (" + placeholders() + "))", b2Codes().toArray());
        for (String code : ADMINISTRATIVE_CODES) {
            jdbcTemplate.update("insert into role_permission (role, permission_id) select 'ADMINISTRATIVO', id from permission where code = ?", code);
        }
        jdbcTemplate.update("delete from role_permission where role = 'ALUMNO' and permission_id in (select id from permission where module = 'billing')");
        cache.invalidate();
    }

    private static List<String> b2Codes() {
        List<String> all = new ArrayList<>(ADMINISTRATIVE_CODES);
        all.addAll(DIRECTOR_ONLY_CODES);
        return all;
    }

    private static String placeholders() {
        return String.join(",", Collections.nCopies(b2Codes().size(), "?"));
    }

    private List<AuthorizationMatrixRow> b2Rows() {
        Map<String, MappedEndpoint> endpoints = endpointsByKey();
        Set<String> codes = Set.copyOf(b2Codes());
        return AuthorizationMatrixFile.read().stream()
                .filter(row -> endpoints.containsKey(row.key()))
                .filter(row -> {
                    RequiresPermission annotation = endpoints.get(row.key()).handler().getMethodAnnotation(RequiresPermission.class);
                    return annotation != null && codes.contains(annotation.code());
                })
                .toList();
    }

    private Map<String, MappedEndpoint> endpointsByKey() {
        return mappedEndpoints().stream().collect(Collectors.toMap(MappedEndpoint::key, Function.identity(), (first, second) -> first));
    }

    private int statusFor(Map<String, MappedEndpoint> endpoints, AuthorizationMatrixRow row, MatrixRole role) throws Exception {
        return statusOf(endpoints.get(row.key()), row.body() == null ? "" : row.body(), role);
    }

    private boolean isForbiddenByTheGate(Map<String, MappedEndpoint> endpoints, AuthorizationMatrixRow row, MatrixRole role) throws Exception {
        int status = statusFor(endpoints, row, role);
        return status == 403 || (role == null && status == 401);
    }

    private void setModuleMode(String mode) {
        for (String role : List.of("ALUMNO", "DOCENTE", "PRECEPTOR", "ADMINISTRATIVO", "DIRECTOR", "PLATFORM_ADMIN")) {
            jdbcTemplate.update("insert into permission_module_mode (role, module, mode) values (?, ?, ?)", role, "billing", mode);
        }
        cache.invalidate();
    }

    private AuthorizationMatrixRow rowOf(String key) {
        return b2Rows().stream().filter(row -> row.key().equals(key)).findFirst().orElseThrow();
    }

    private void grantToRole(String role, String code) {
        jdbcTemplate.update("insert into role_permission (role, permission_id) select ?, id from permission where code = ?", role, code);
        cache.invalidate();
    }

    private void revokeFromRole(String role, String code) {
        jdbcTemplate.update("delete from role_permission where role = ? and permission_id = (select id from permission where code = ?)", role, code);
        cache.invalidate();
    }

    private void assertDecisionsMatchLegacy(String mode) throws Exception {
        List<AuthorizationMatrixRow> rows = b2Rows();
        Map<String, MappedEndpoint> endpoints = endpointsByKey();
        assertThat(rows).hasSize(29);
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
    void legacyModeMatchesTheOldRulesForEveryMonetaryBillingEndpointAndRole() throws Exception {
        assertDecisionsMatchLegacy("LEGACY");
    }

    @Test
    void shadowModeMatchesTheOldRulesForEveryMonetaryBillingEndpointAndRole() throws Exception {
        setModuleMode("SHADOW");
        assertDecisionsMatchLegacy("SHADOW");
    }

    @Test
    void enforceModeWithTheDefaultGridMatchesTheOldRulesForEveryMonetaryBillingEndpointAndRole() throws Exception {
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
    void everyPermissionOfTheBatchIsSensitiveAndOnlyTheAdministrativeOnesAreSeeded() {
        List<String> sensitive = jdbcTemplate.queryForList("select code from permission where sensitive and code in (" + placeholders() + ")", String.class, b2Codes().toArray());
        assertThat(sensitive).containsExactlyInAnyOrderElementsOf(b2Codes());
        List<String> seeded = jdbcTemplate.queryForList(
                "select p.code from role_permission rp join permission p on p.id = rp.permission_id where p.code in (" + placeholders() + ")", String.class, b2Codes().toArray());
        assertThat(seeded).containsExactlyInAnyOrderElementsOf(ADMINISTRATIVE_CODES);
    }

    @Test
    void enforceModeRefusesToDelegateADirectorOnlyPermissionAndFollowsTheGridWhenAnAdministrativeGrantIsRemoved() throws Exception {
        setModuleMode("ENFORCE");
        Map<String, MappedEndpoint> endpoints = endpointsByKey();
        AuthorizationMatrixRow executionRow = rowOf("POST /billing-runs/executions");
        assertThat(isForbiddenByTheGate(endpoints, executionRow, MatrixRole.ADMINISTRATIVO)).isTrue();
        assertThat(tryToDelegateThroughTheGrid("ADMINISTRATIVO", "billing.run_billing")).isEqualTo(422);
        assertThat(lastResponseBody).contains("FIXED_TO_DIRECTOR_PERMISSION_NOT_EDITABLE");
        assertThat(isForbiddenByTheGate(endpoints, executionRow, MatrixRole.ADMINISTRATIVO)).isTrue();
        assertThat(isForbiddenByTheGate(endpoints, executionRow, MatrixRole.DIRECTOR)).isFalse();

        AuthorizationMatrixRow revocationRow = rowOf("POST /student-discounts/{id}/revocation");
        assertThat(isForbiddenByTheGate(endpoints, revocationRow, MatrixRole.ADMINISTRATIVO)).isFalse();
        revokeFromRole("ADMINISTRATIVO", "billing.revoke_student_discount");
        assertThat(isForbiddenByTheGate(endpoints, revocationRow, MatrixRole.ADMINISTRATIVO)).isTrue();
        assertThat(isForbiddenByTheGate(endpoints, revocationRow, MatrixRole.DIRECTOR)).isFalse();
    }

    @Test
    void enforceModeLetsADelegatedRoleReachTheServicesWithoutAServiceLevelRule() throws Exception {
        setModuleMode("ENFORCE");
        Map<String, MappedEndpoint> endpoints = endpointsByKey();
        List<AuthorizationMatrixRow> rows = List.of(
                rowOf("POST /program-enrollments/{id}/student-discounts"), rowOf("POST /program-enrollments/{id}/advance-enrollment-fee"));
        for (AuthorizationMatrixRow row : rows) {
            assertThat(statusFor(endpoints, row, MatrixRole.PRECEPTOR)).as(row.key()).isEqualTo(403);
        }
        for (String code : List.of("billing.grant_student_discount", "billing.request_advance_enrollment_fee")) {
            grantToRole("PRECEPTOR", code);
        }
        for (AuthorizationMatrixRow row : rows) {
            assertThat(statusFor(endpoints, row, MatrixRole.PRECEPTOR)).as(row.key()).isNotEqualTo(403);
        }
    }

    @Test
    void enforceModeRefusesToDelegateTheDecisionAndTheDomainRefusesAStaleGrantToDecideTheAuthorizationRequest() throws Exception {
        setModuleMode("ENFORCE");
        Map<String, MappedEndpoint> endpoints = endpointsByKey();
        MappedEndpoint approve = endpoints.get("POST /authorization-requests/{id}/approve");
        Long requestId = ownership.resourceIdFor(OwnershipKind.AUTHORIZATION_REQUEST);
        String token = fixtures.tokenFor(MatrixRole.PRECEPTOR);

        assertThat(statusWithToken(approve, "", token, requestId)).isEqualTo(403);
        assertThat(lastResponseBody).doesNotContain("AuthorizationRequestAccessDeniedException");

        assertThat(tryToDelegateThroughTheGrid("PRECEPTOR", "billing.decide_authorization_request")).isEqualTo(422);
        grantToRole("PRECEPTOR", "billing.decide_authorization_request");

        assertThat(statusWithToken(approve, "", token, requestId)).isEqualTo(403);
        assertThat(lastResponseBody).contains("AuthorizationRequestAccessDeniedException");
        assertThat(jdbcTemplate.queryForObject("select status from authorization_request where id = ?", String.class, requestId)).isEqualTo("PENDING");
    }

    @Test
    void theDirectorIsNeverDeniedByLegacyOnTheMonetaryBillingEndpointsOfThisBatch() {
        List<String> deniedToTheDirectorInLegacy = b2Rows().stream()
                .filter(row -> row.byRole().get(MatrixRole.DIRECTOR) == AccessExpectation.DENY)
                .map(AuthorizationMatrixRow::key)
                .toList();
        assertThat(deniedToTheDirectorInLegacy).isEmpty();
    }
}
