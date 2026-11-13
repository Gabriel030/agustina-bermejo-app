package com.red_escuela.backend.security;

import com.red_escuela.backend.billing.guard.BillingEndpointClassificationFile;
import com.red_escuela.backend.billing.guard.BillingEndpointClassificationFile.Classification;
import com.red_escuela.backend.platform.infrastructure.FeatureSnapshotHolder;
import com.red_escuela.backend.testsupport.SlowTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SlowTest(reason = "una request MockMvc por endpoint de billing contra el contexto completo")
class BillingMirrorEndpointsIT extends AuthorizationMatrixSupport {

    private static final String BILLING_PACKAGE = "com.red_escuela.backend.billing.";

    private static final List<String> BILLING_TABLES = List.of("invoice", "payment", "credit_note", "refund", "chargeback", "student_discount",
            "discount_definition", "monthly_invoice_run", "nightly_billing_run", "bank_transfer_receipt", "payment_plan_option",
            "payment_plan_selection", "promotion", "annual_upfront_request", "authorization_request", "payment_gateway_config");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private FeatureSnapshotHolder snapshotHolder;

    @BeforeEach
    void switchBillingToMirror() {
        committed(() -> jdbc.update("update platform_feature set enabled = true, mode = 'MIRROR' where code = 'BILLING'"));
        snapshotHolder.refresh();
    }

    @AfterEach
    void restoreBillingFull() {
        committed(() -> jdbc.update("update platform_feature set enabled = true, mode = 'FULL', version = 0 where code = 'BILLING'"));
        snapshotHolder.refresh();
    }

    @Test
    void mirrorModeServesReadsAndHidesWritesWithoutTouchingData() throws Exception {
        Map<String, Classification> classified = BillingEndpointClassificationFile.read();
        Map<String, AuthorizationMatrixRow> rowsByKey = AuthorizationMatrixFile.read().stream()
                .collect(Collectors.toMap(AuthorizationMatrixRow::key, Function.identity(), (first, second) -> first));
        Map<String, Integer> countsBefore = countRows();
        List<String> readsHidden = new ArrayList<>();
        List<String> writesServed = new ArrayList<>();
        int reads = 0;
        int writes = 0;
        for (MappedEndpoint endpoint : mappedEndpoints()) {
            if (!endpoint.handler().getBeanType().getName().startsWith(BILLING_PACKAGE)) {
                continue;
            }
            String classificationKey = endpoint.handler().getBeanType().getSimpleName() + "." + endpoint.handler().getMethod().getName() + " " + endpoint.key();
            Classification classification = classified.get(classificationKey);
            assertThat(classification).as("classification of " + classificationKey).isNotNull();
            if (classification == Classification.EXCEPTION_OFF) {
                continue;
            }
            AuthorizationMatrixRow row = rowsByKey.get(endpoint.key());
            assertThat(row).as("matrix row of " + endpoint.key()).isNotNull();
            MatrixRole role = roleAllowedOn(row);
            assertThat(role).as("role allowed on " + endpoint.key()).isNotNull();
            statusOf(endpoint, row.body(), role);
            boolean hiddenByGuard = lastResponseBody.contains("NoResourceFoundException");
            if (classification == Classification.READ) {
                reads++;
                if (hiddenByGuard) {
                    readsHidden.add(classificationKey);
                }
            } else {
                writes++;
                if (!hiddenByGuard) {
                    writesServed.add(classificationKey);
                }
            }
        }

        assertThat(reads).isGreaterThan(50);
        assertThat(writes).isGreaterThan(40);
        assertThat(readsHidden).isEmpty();
        assertThat(writesServed).isEmpty();
        assertThat(countRows()).isEqualTo(countsBefore);
    }

    private Map<String, Integer> countRows() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        List<String> existing = jdbc.queryForList("select table_name from information_schema.tables where table_schema = current_schema()", String.class);
        List<String> tables = BILLING_TABLES.stream().filter(existing::contains).toList();
        assertThat(tables).hasSizeGreaterThanOrEqualTo(10).contains("invoice", "payment", "refund", "credit_note");
        for (String table : tables) {
            counts.put(table, jdbc.queryForObject("select count(*) from " + table, Integer.class));
        }
        return counts;
    }

    private static MatrixRole roleAllowedOn(AuthorizationMatrixRow row) {
        for (MatrixRole role : MatrixRole.values()) {
            AccessExpectation expectation = row.byRole().get(role);
            if (expectation == AccessExpectation.ALLOW || expectation == AccessExpectation.OWNER) {
                return role;
            }
        }
        return null;
    }

    private void committed(Runnable work) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.executeWithoutResult(status -> work.run());
    }
}
