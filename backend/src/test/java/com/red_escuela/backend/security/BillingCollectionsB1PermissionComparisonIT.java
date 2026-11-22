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
class BillingCollectionsB1PermissionComparisonIT extends AuthorizationMatrixSupport {

    private static final Set<String> B1_CODES = Set.of(
            "billing.register_cash_payment", "billing.register_annual_upfront_cash_payment", "billing.submit_bank_transfer_receipt",
            "billing.review_bank_transfer_receipt", "billing.revoke_payment_approval", "billing.register_chargeback",
            "billing.reverse_chargeback", "billing.request_refund", "billing.complete_refund", "billing.fail_refund",
            "billing.issue_credit_note", "billing.cancel_invoice", "billing.allocate_account_credit");

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PermissionSnapshotCache cache;

    @AfterEach
    void restoreDefaultModes() {
        jdbcTemplate.update("delete from permission_module_mode where module = 'billing'");
        jdbcTemplate.update("delete from role_permission where role in ('ALUMNO', 'PRECEPTOR', 'DOCENTE') and permission_id in (select id from permission where module = 'billing')");
        jdbcTemplate.update("delete from role_permission where role = 'ADMINISTRATIVO' and permission_id in (select id from permission where code = 'billing.revoke_payment_approval')");
        cache.invalidate();
    }

    private List<AuthorizationMatrixRow> billingRows() {
        Map<String, MappedEndpoint> endpoints = endpointsByKey();
        return AuthorizationMatrixFile.read().stream()
                .filter(row -> endpoints.containsKey(row.key()))
                .filter(row -> {
                    RequiresPermission annotation = endpoints.get(row.key()).handler().getMethodAnnotation(RequiresPermission.class);
                    return annotation != null && B1_CODES.contains(annotation.code());
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
        assertThat(rows).hasSize(14);
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
        AuthorizationMatrixRow cashRow = rowOf("POST /students/{studentId}/cash-payments");
        assertThat(isForbiddenByTheGate(endpoints, cashRow, MatrixRole.PRECEPTOR)).isTrue();
        assertThat(isForbiddenByTheGate(endpoints, cashRow, MatrixRole.ADMINISTRATIVO)).isFalse();
        grantToRole("PRECEPTOR", "billing.register_cash_payment");
        assertThat(isForbiddenByTheGate(endpoints, cashRow, MatrixRole.PRECEPTOR)).isFalse();
        assertThat(isForbiddenByTheGate(endpoints, rowOf("POST /refunds/{refundId}/completion"), MatrixRole.PRECEPTOR)).isTrue();
    }

    @Test
    void enforceModeLetsADelegatedRoleReachTheServicesWithoutAServiceLevelRule() throws Exception {
        setModuleMode("ENFORCE");
        Map<String, MappedEndpoint> endpoints = endpointsByKey();
        Map<String, String> permissionByEndpoint = Map.of(
                "POST /refunds/{refundId}/completion", "billing.complete_refund",
                "POST /refunds/{refundId}/failure", "billing.fail_refund",
                "POST /chargebacks/{chargebackId}/reversal", "billing.reverse_chargeback",
                "POST /payments/{paymentId}/chargebacks", "billing.register_chargeback",
                "POST /invoices/{invoiceId}/credit-notes", "billing.issue_credit_note",
                "POST /invoices/{invoiceId}/cancellations", "billing.cancel_invoice",
                "POST /students/{studentId}/current-account/allocations", "billing.allocate_account_credit",
                "POST /students/{studentId}/refund-requests", "billing.request_refund");
        for (Map.Entry<String, String> entry : permissionByEndpoint.entrySet()) {
            AuthorizationMatrixRow row = rowOf(entry.getKey());
            assertThat(isForbiddenByTheGate(endpoints, row, MatrixRole.PRECEPTOR)).as(entry.getKey()).isTrue();
            grantToRole("PRECEPTOR", entry.getValue());
            assertThat(isForbiddenByTheGate(endpoints, row, MatrixRole.PRECEPTOR)).as(entry.getKey()).isFalse();
            assertThat(isForbiddenByTheGate(endpoints, row, MatrixRole.DOCENTE)).as(entry.getKey()).isTrue();
        }
    }

    @Test
    void enforceModeKeepsThePaymentApprovalRevocationOutOfTheAdministrativeRoleUntilItIsGranted() throws Exception {
        setModuleMode("ENFORCE");
        Map<String, MappedEndpoint> endpoints = endpointsByKey();
        AuthorizationMatrixRow row = rowOf("POST /payments/{paymentId}/approval-revocation");
        assertThat(isForbiddenByTheGate(endpoints, row, MatrixRole.ADMINISTRATIVO)).isTrue();
        assertThat(isForbiddenByTheGate(endpoints, row, MatrixRole.DIRECTOR)).isFalse();
        grantToRole("ADMINISTRATIVO", "billing.revoke_payment_approval");
        assertThat(isForbiddenByTheGate(endpoints, row, MatrixRole.ADMINISTRATIVO)).isFalse();
    }

    @Test
    void theDirectorIsNeverDeniedByLegacyOnTheBillingCollectionEndpointsOfThisBatch() {
        List<String> deniedToTheDirectorInLegacy = billingRows().stream()
                .filter(row -> row.byRole().get(MatrixRole.DIRECTOR) == AccessExpectation.DENY)
                .map(AuthorizationMatrixRow::key)
                .toList();
        assertThat(deniedToTheDirectorInLegacy).isEmpty();
    }
}
