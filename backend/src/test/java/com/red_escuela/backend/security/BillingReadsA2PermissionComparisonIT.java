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
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SlowTest(reason = "recorre las lecturas y previews de billing (tanda A2) por rol contra el contexto completo")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class BillingReadsA2PermissionComparisonIT extends AuthorizationMatrixSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PermissionSnapshotCache cache;

    private static final List<String> A2_CODES = List.of(
            "billing.read_student_billing", "billing.read_bank_transfer_receipts", "billing.read_prices", "billing.preview_price_change",
            "billing.read_discounts", "billing.validate_discount_definition", "billing.preview_student_discount", "billing.preview_payment_reversal",
            "billing.preview_approval_revocation", "billing.preview_cash_payment", "billing.preview_refund", "billing.preview_invoice_cancellation",
            "billing.read_authorization_requests", "billing.read_annual_upfront", "billing.read_annual_upfront_terms_versions", "billing.read_year_billing_templates");

    private static final String A2_CODES_SQL = "('" + String.join("', '", A2_CODES) + "')";

    private static final Set<String> DIRECTOR_ONLY_CODES = Set.of("billing.preview_approval_revocation", "billing.read_annual_upfront_terms_versions");

    @AfterEach
    void restoreDefaultModes() {
        jdbcTemplate.update("delete from permission_module_mode where module = 'billing'");
        jdbcTemplate.update("delete from role_permission where permission_id in (select id from permission where code in " + A2_CODES_SQL + ")");
        jdbcTemplate.update("insert into role_permission (role, permission_id) select 'ADMINISTRATIVO', id from permission where code in " + A2_CODES_SQL
                + " and code not in ('billing.preview_approval_revocation', 'billing.read_annual_upfront_terms_versions')");
        cache.invalidate();
    }

    private List<AuthorizationMatrixRow> billingRows() {
        Map<String, MappedEndpoint> endpoints = endpointsByKey();
        return AuthorizationMatrixFile.read().stream()
                .filter(row -> endpoints.containsKey(row.key()))
                .filter(row -> {
                    RequiresPermission gate = endpoints.get(row.key()).handler().getMethodAnnotation(RequiresPermission.class);
                    return gate != null && A2_CODES.contains(gate.code());
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

    private AuthorizationMatrixRow rowOf(String key) {
        return billingRows().stream().filter(row -> row.key().equals(key)).findFirst().orElseThrow();
    }

    private void setModuleMode(String mode) {
        for (String role : List.of("ALUMNO", "DOCENTE", "PRECEPTOR", "ADMINISTRATIVO", "DIRECTOR", "PLATFORM_ADMIN")) {
            jdbcTemplate.update("insert into permission_module_mode (role, module, mode) values (?, ?, ?)", role, "billing", mode);
        }
        cache.invalidate();
    }

    private void grant(String role, String code) {
        jdbcTemplate.update("insert into role_permission (role, permission_id) select ?, id from permission where code = ?", role, code);
        cache.invalidate();
    }

    private void assertDecisionsMatchLegacy(String mode) throws Exception {
        List<AuthorizationMatrixRow> rows = billingRows();
        Map<String, MappedEndpoint> endpoints = endpointsByKey();
        assertThat(rows).hasSize(40);
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
    void legacyModeMatchesTheOldRulesForEveryBillingReadAndPreviewAndRole() throws Exception {
        assertDecisionsMatchLegacy("LEGACY");
    }

    @Test
    void shadowModeMatchesTheOldRulesForEveryBillingReadAndPreviewAndRole() throws Exception {
        setModuleMode("SHADOW");
        assertDecisionsMatchLegacy("SHADOW");
    }

    @Test
    void enforceModeWithTheDefaultGridMatchesTheOldRulesForEveryBillingReadAndPreviewAndRole() throws Exception {
        setModuleMode("ENFORCE");
        assertDecisionsMatchLegacy("ENFORCE");
    }

    @Test
    void legacyModeIgnoresTheGridGrantedToAnotherRole() throws Exception {
        for (String code : A2_CODES) {
            grant("ALUMNO", code);
        }
        assertDecisionsMatchLegacy("LEGACY");
    }

    @Test
    void enforceModeFollowsTheGridWhenAPermissionIsRemovedFromAdministrativo() throws Exception {
        setModuleMode("ENFORCE");
        Map<String, MappedEndpoint> endpoints = endpointsByKey();
        AuthorizationMatrixRow pricesRow = rowOf("GET /prices");
        assertThat(isForbiddenByTheGate(endpoints, pricesRow, MatrixRole.ADMINISTRATIVO)).isFalse();
        jdbcTemplate.update("delete from role_permission where role = 'ADMINISTRATIVO' and permission_id in (select id from permission where code = 'billing.read_prices')");
        cache.invalidate();
        assertThat(isForbiddenByTheGate(endpoints, pricesRow, MatrixRole.ADMINISTRATIVO)).isTrue();
        assertThat(isForbiddenByTheGate(endpoints, pricesRow, MatrixRole.DIRECTOR)).isFalse();
    }

    @Test
    void enforceModeLetsADelegatedRoleReadDiscountDefinitions() throws Exception {
        setModuleMode("ENFORCE");
        Map<String, MappedEndpoint> endpoints = endpointsByKey();
        AuthorizationMatrixRow row = rowOf("GET /discount-definitions");
        assertThat(isForbiddenByTheGate(endpoints, row, MatrixRole.PRECEPTOR)).isTrue();
        grant("PRECEPTOR", "billing.read_discounts");
        assertThat(isForbiddenByTheGate(endpoints, row, MatrixRole.PRECEPTOR)).isFalse();
    }

    @Test
    void enforceModeLetsADelegatedRoleThroughEndpointsWhoseOldRuleLivedInTheService() throws Exception {
        setModuleMode("ENFORCE");
        Map<String, MappedEndpoint> endpoints = endpointsByKey();
        List<String[]> delegations = List.of(
                new String[]{"GET /annual-upfront-requests", "billing.read_annual_upfront"},
                new String[]{"POST /invoices/{invoiceId}/cancellations/preview", "billing.preview_invoice_cancellation"},
                new String[]{"GET /students/{studentId}/refunds", "billing.read_student_billing"},
                new String[]{"POST /students/{studentId}/cash-payments/preview", "billing.preview_cash_payment"},
                new String[]{"GET /prices", "billing.read_prices"});
        for (String[] delegation : delegations) {
            AuthorizationMatrixRow row = rowOf(delegation[0]);
            assertThat(isForbiddenByTheGate(endpoints, row, MatrixRole.DOCENTE)).as(delegation[0] + " before").isTrue();
            grant("DOCENTE", delegation[1]);
            assertThat(isForbiddenByTheGate(endpoints, row, MatrixRole.DOCENTE)).as(delegation[0] + " after").isFalse();
        }
    }

    @Test
    void enforceModeLetsADelegatedRoleThroughTheDirectorOnlyEndpoints() throws Exception {
        setModuleMode("ENFORCE");
        Map<String, MappedEndpoint> endpoints = endpointsByKey();
        for (String code : DIRECTOR_ONLY_CODES) {
            String key = code.equals("billing.preview_approval_revocation") ? "POST /payments/{paymentId}/approval-revocation/preview" : "GET /annual-upfront-terms/versions";
            AuthorizationMatrixRow row = rowOf(key);
            assertThat(isForbiddenByTheGate(endpoints, row, MatrixRole.ADMINISTRATIVO)).as(key + " before").isTrue();
            grant("ADMINISTRATIVO", code);
            assertThat(isForbiddenByTheGate(endpoints, row, MatrixRole.ADMINISTRATIVO)).as(key + " after").isFalse();
        }
    }
}
