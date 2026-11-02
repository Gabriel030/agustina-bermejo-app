package com.red_escuela.backend.billing.payment.service;

import com.red_escuela.backend.billing.controller.MpWebhookController;
import com.red_escuela.backend.billing.exception.InvalidMpWebhookSignatureException;
import com.red_escuela.backend.billing.infrastructure.MercadoPagoPaymentFetcher;
import com.red_escuela.backend.billing.infrastructure.MpWebhookSignatureVerifier;
import com.red_escuela.backend.billing.operation.ApprovedPaymentRegistrationPort;
import com.red_escuela.backend.billing.payment.repository.MpWebhookReceivedEventRepository;
import com.red_escuela.backend.billing.payment.usecase.RecordMpWebhookEventService;
import com.red_escuela.backend.billing.usecase.paymentgateway.ProcessMpWebhookService;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.platform.infrastructure.FeatureSnapshotHolder;
import com.red_escuela.backend.shared.module.CurrentBillingModePort;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class MpWebhookRawEventStoreIT extends AbstractIntegrationTest {

    private static final List<String> BILLING_MONEY_TABLES = List.of(
            "invoice", "payment", "payment_allocation", "student_current_account", "current_account_entry");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private FeatureSnapshotHolder snapshotHolder;

    @Autowired
    private CurrentBillingModePort currentBillingModePort;

    @Autowired
    private ApprovedPaymentRegistrationPort registrationPort;

    @Autowired
    private MpWebhookReceivedEventRepository receivedEventRepository;

    @Autowired
    private java.time.Clock clock;

    @AfterEach
    void restoreBillingFull() {
        switchBillingTo("FULL");
        jdbc.update("delete from mp_webhook_received_event");
    }

    private void switchBillingTo(String mode) {
        boolean enabled = !"OFF".equals(mode);
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.executeWithoutResult(status ->
                jdbc.update("update platform_feature set enabled = ?, mode = ? where code = 'BILLING'", enabled, mode));
        snapshotHolder.refresh();
    }

    private MpWebhookController controllerWithSecret(String secret) {
        MpWebhookSignatureVerifier verifier = new MpWebhookSignatureVerifier(secret, secret.isEmpty());
        MercadoPagoPaymentFetcher fetcherThatMustNotRun = mpPaymentId -> {
            throw new AssertionError("The payment gateway must not be queried: " + mpPaymentId);
        };
        MpWebhookRawEventRecorder recorder = new MpWebhookRawEventRecorder(verifier, receivedEventRepository, clock);
        return new MpWebhookController(new ProcessMpWebhookService(registrationPort, fetcherThatMustNotRun, verifier),
                new RecordMpWebhookEventService(currentBillingModePort, recorder));
    }

    private Map<String, Long> billingMoneyRowCounts() {
        Map<String, Long> counts = new java.util.LinkedHashMap<>();
        BILLING_MONEY_TABLES.forEach(table -> counts.put(table, jdbc.queryForObject("select count(*) from " + table, Long.class)));
        return counts;
    }

    private void receive(MpWebhookController controller, String xSignature, String xRequestId, String mpPaymentId) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> controller.webhook(xSignature, xRequestId, mpPaymentId));
    }

    private void assertStoresRawEventWithoutTouchingBilling(String mode) {
        switchBillingTo(mode);
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 11, 1, 10, 30));
        Map<String, Long> before = billingMoneyRowCounts();

        receive(controllerWithSecret(""), "ts=1,v1=abc", "request-" + mode, "mp-" + mode);

        assertThat(billingMoneyRowCounts()).isEqualTo(before);
        Map<String, Object> stored = jdbc.queryForMap("select * from mp_webhook_received_event");
        assertThat(stored.get("mp_payment_id")).isEqualTo("mp-" + mode);
        assertThat(stored.get("x_request_id")).isEqualTo("request-" + mode);
        assertThat(stored.get("x_signature")).isEqualTo("ts=1,v1=abc");
        assertThat(stored.get("billing_mode_at_receipt")).isEqualTo(mode);
        assertThat(stored.get("processing_status")).isEqualTo("RECEIVED");
        assertThat(stored.get("received_at")).isEqualTo(java.sql.Timestamp.valueOf(LocalDateTime.of(2026, 11, 1, 10, 30)));
    }

    @Test
    void aWebhookReceivedWhileBillingIsOffStoresTheRawEventAndLeavesBillingUntouched() {
        assertStoresRawEventWithoutTouchingBilling("OFF");
    }

    @Test
    void aWebhookReceivedWhileBillingIsInMirrorStoresTheRawEventAndLeavesBillingUntouched() {
        assertStoresRawEventWithoutTouchingBilling("MIRROR");
    }

    @Test
    void aWebhookWithAnInvalidSignatureReceivedWhileBillingIsNotFullIsRejectedAndNothingIsStored() {
        for (String mode : List.of("OFF", "MIRROR")) {
            switchBillingTo(mode);
            MpWebhookController controller = controllerWithSecret("webhook-secret");

            assertThatThrownBy(() -> receive(controller, "ts=1,v1=forged", "request-1", "mp-forged"))
                    .isInstanceOf(InvalidMpWebhookSignatureException.class);

            assertThat(jdbc.queryForObject("select count(*) from mp_webhook_received_event", Long.class)).isZero();
        }
    }

    @Test
    void aWebhookReceivedWhileBillingIsFullStoresNoRawEventAndStillQueriesTheGateway() {
        switchBillingTo("FULL");

        assertThatThrownBy(() -> receive(controllerWithSecret(""), "", "request-1", "mp-full"))
                .isInstanceOf(AssertionError.class);

        assertThat(jdbc.queryForObject("select count(*) from mp_webhook_received_event", Long.class)).isZero();
    }
}
