package com.red_escuela.backend.billing.guard;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.billing.collections.service.LateFeeCorrectionOnWithdrawalListener;
import com.red_escuela.backend.billing.events.EnrollmentWithdrawnForBillingEvent;
import com.red_escuela.backend.platform.infrastructure.FeatureSnapshotHolder;
import com.red_escuela.backend.shared.module.BillingMode;
import com.red_escuela.backend.shared.module.RequiresModule;
import com.red_escuela.backend.testsupport.ImportedApplicationClasses;
import com.red_escuela.backend.testsupport.SlowTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SlowTest(reason = "Boots the full Spring context to prove the guard applies to real beans and to the asynchronous module listener")
@SpringBootTest
@ActiveProfiles("test")
class BillingJobsAndListenersModeIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private FeatureSnapshotHolder snapshotHolder;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private LateFeeCorrectionOnWithdrawalListener withdrawalListener;

    @AfterEach
    void restoreBillingFull() {
        switchBillingTo("FULL", true);
    }

    @Test
    void everyBeanWithAGuardedJobOrListenerIsAProxyThatCanApplyTheGuard() {
        List<String> notProxied = ImportedApplicationClasses.main().stream()
                .filter(javaClass -> javaClass.getPackageName().startsWith("com.red_escuela.backend.billing"))
                .filter(javaClass -> javaClass.getMethods().stream().anyMatch(method -> method.isAnnotatedWith(RequiresModule.class)))
                .filter(javaClass -> !javaClass.getSimpleName().endsWith("Controller"))
                .map(javaClass -> javaClass.reflect())
                .filter(type -> !context.getBeansOfType(type).isEmpty())
                .filter(type -> context.getBeansOfType(type).values().stream().noneMatch(AopUtils::isAopProxy))
                .map(Class::getName)
                .toList();

        assertThat(notProxied).isEmpty();
    }

    @Test
    void listenerWithProcessedEffectGuardLeavesTheEventUnclaimedWhileBillingIsOffOrMirrorAndClaimsItInFull() {
        EnrollmentWithdrawnForBillingEvent whileOff = withdrawalEvent();
        EnrollmentWithdrawnForBillingEvent whileMirror = withdrawalEvent();
        EnrollmentWithdrawnForBillingEvent whileFull = withdrawalEvent();

        switchBillingTo("OFF", false);
        withdrawalListener.correctLateFeesOfWithdrawnEnrollment(whileOff);
        switchBillingTo("MIRROR", true);
        withdrawalListener.correctLateFeesOfWithdrawnEnrollment(whileMirror);
        switchBillingTo("FULL", true);
        withdrawalListener.correctLateFeesOfWithdrawnEnrollment(whileFull);

        await().atMost(Duration.ofSeconds(10)).until(() -> isClaimed(whileFull));
        assertThat(isClaimed(whileOff)).isFalse();
        assertThat(isClaimed(whileMirror)).isFalse();
    }

    private boolean isClaimed(EnrollmentWithdrawnForBillingEvent event) {
        return jdbc.queryForObject("select count(*) from processed_effect where event_id = ?", Integer.class, event.eventId()) > 0;
    }

    private static EnrollmentWithdrawnForBillingEvent withdrawalEvent() {
        LocalDate day = LocalDate.of(2027, 3, 10);
        return new EnrollmentWithdrawnForBillingEvent(UUID.randomUUID(), "c", Instant.parse("2027-03-10T12:00:00Z"), 987654L, 987654L, null, day, day, "WITHDRAWAL");
    }

    private void switchBillingTo(String mode, boolean enabled) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.executeWithoutResult(status ->
                jdbc.update("update platform_feature set enabled = ?, mode = ? where code = 'BILLING'", enabled, mode));
        snapshotHolder.refresh();
    }
}
