package com.red_escuela.backend.security;

import com.red_escuela.backend.shared.module.RequiresModule;
import com.red_escuela.backend.platform.infrastructure.FeatureSnapshotHolder;
import com.red_escuela.backend.shared.module.BillingMode;
import com.red_escuela.backend.testsupport.SlowTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SlowTest(reason = "una request MockMvc por endpoint de billing contra el contexto completo")
class BillingOffEndpointsIT extends AuthorizationMatrixSupport {

    private static final String BILLING_PACKAGE = "com.red_escuela.backend.billing.";

    private static final String GATEWAY_WEBHOOK = "POST /payments/mp-webhook";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private FeatureSnapshotHolder snapshotHolder;

    @BeforeEach
    void switchBillingOff() {
        committed(() -> jdbc.update("update platform_feature set enabled = false, mode = 'OFF' where code = 'BILLING'"));
        snapshotHolder.refresh();
    }

    @AfterEach
    void restoreBillingFull() {
        committed(() -> jdbc.update("update platform_feature set enabled = true, mode = 'FULL', version = 0 where code = 'BILLING'"));
        snapshotHolder.refresh();
    }

    @Test
    void everyBillingEndpointAnswersNotFoundWhileBillingIsOff() throws Exception {
        Map<String, AuthorizationMatrixRow> rowsByKey = AuthorizationMatrixFile.read().stream()
                .collect(Collectors.toMap(AuthorizationMatrixRow::key, Function.identity(), (first, second) -> first));
        List<String> notHidden = new ArrayList<>();
        List<String> withoutMatrixRow = new ArrayList<>();
        int checked = 0;
        for (MappedEndpoint endpoint : mappedEndpoints()) {
            if (!endpoint.handler().getBeanType().getName().startsWith(BILLING_PACKAGE) || endpoint.key().equals(GATEWAY_WEBHOOK)) {
                continue;
            }
            AuthorizationMatrixRow row = rowsByKey.get(endpoint.key());
            if (row == null) {
                withoutMatrixRow.add(endpoint.key());
                continue;
            }
            MatrixRole role = roleAllowedOn(row);
            if (role == null) {
                withoutMatrixRow.add(endpoint.key() + " (no role allowed)");
                continue;
            }
            checked++;
            int status = statusOf(endpoint, row.body(), role);
            if (status != 404) {
                notHidden.add(endpoint.key() + " as " + role + " -> " + status);
            }
        }

        assertThat(withoutMatrixRow).isEmpty();
        assertThat(checked).isGreaterThan(100);
        assertThat(notHidden).isEmpty();
    }

    @Test
    void gatewayWebhookIsTheOnlyBillingEndpointDeclaredAvailableWhileBillingIsOff() {
        List<String> alwaysAvailable = mappedEndpoints().stream()
                .filter(endpoint -> endpoint.handler().getBeanType().getName().startsWith(BILLING_PACKAGE))
                .filter(endpoint -> {
                    RequiresModule requirement = AnnotationUtils.findAnnotation(endpoint.handler().getMethod(), RequiresModule.class);
                    if (requirement == null) {
                        requirement = AnnotationUtils.findAnnotation(endpoint.handler().getBeanType(), RequiresModule.class);
                    }
                    return requirement == null || requirement.minMode() == BillingMode.OFF;
                })
                .map(MappedEndpoint::key)
                .toList();

        assertThat(alwaysAvailable).containsExactly(GATEWAY_WEBHOOK);
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
