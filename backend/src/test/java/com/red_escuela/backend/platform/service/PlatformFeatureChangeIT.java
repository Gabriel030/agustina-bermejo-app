package com.red_escuela.backend.platform.service;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.platform.api.BillingModeGate;
import com.red_escuela.backend.platform.api.FeatureQuery;
import com.red_escuela.backend.platform.domain.BillingModeChangeClearance;
import com.red_escuela.backend.platform.domain.FeatureChange;
import com.red_escuela.backend.platform.domain.FeatureState;
import com.red_escuela.backend.platform.entity.FeatureChangeActorKind;
import com.red_escuela.backend.platform.entity.FeatureChangeLog;
import com.red_escuela.backend.platform.exception.FeatureAlreadyInRequestedStateException;
import com.red_escuela.backend.platform.exception.FeatureChangeReasonInvalidException;
import com.red_escuela.backend.platform.exception.FeatureValueInvalidException;
import com.red_escuela.backend.platform.exception.InvalidBillingModeTransitionException;
import com.red_escuela.backend.platform.exception.StaleFeatureVersionException;
import com.red_escuela.backend.platform.infrastructure.FeatureSnapshotHolder;
import com.red_escuela.backend.platform.model.BillingMode;
import com.red_escuela.backend.platform.model.FeatureCode;
import com.red_escuela.backend.platform.repository.FeatureChangeLogRepository;
import com.red_escuela.backend.platform.repository.PlatformFeatureRepository;
import com.red_escuela.backend.platform.usecase.ChangePlatformFeatureUseCase;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.time.MutableClock;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class PlatformFeatureChangeIT extends AbstractIntegrationTest {

    @Autowired
    private ChangePlatformFeatureUseCase changeFeature;

    @Autowired
    private FeatureSnapshotHolder snapshotHolder;

    @Autowired
    private BillingModeGate billingModeGate;

    @Autowired
    private FeatureQuery featureQuery;

    @Autowired
    private PlatformFeatureRepository featureRepository;

    @Autowired
    private FeatureChangeLogRepository changeLogRepository;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private Long actorId;

    @BeforeEach
    void authenticateAsPlatformAdmin() {
        actorId = transactions.execute(status -> persistPlatformAdmin());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actorId.toString(), null));
        snapshotHolder.refresh();
    }

    @AfterEach
    void restoreSeedState() {
        SecurityContextHolder.clearContext();
        jdbc.update("update platform_feature set enabled = true, mode = 'FULL', version = 0 where code = 'BILLING'");
        jdbc.update("update platform_feature set enabled = false, version = 0 where code = 'PAYMENT_METHOD_GATEWAY'");
        jdbc.update("delete from feature_change_log");
        snapshotHolder.refresh();
    }

    @Test
    void everyFeatureCodeHasASeedRowAndBillingStartsInFullForExistingInstallations() {
        assertThat(featureRepository.findAll()).extracting(feature -> feature.getCode()).containsExactlyInAnyOrder(FeatureCode.values());
        assertThat(billingModeGate.currentMode()).isEqualTo(BillingMode.FULL);
        assertThat(featureQuery.isEnabled(FeatureCode.PAYMENT_METHOD_CASH)).isTrue();
        assertThat(featureQuery.isEnabled(FeatureCode.PAYMENT_METHOD_BANK_TRANSFER)).isTrue();
        assertThat(featureQuery.isEnabled(FeatureCode.PAYMENT_METHOD_GATEWAY)).isFalse();
    }

    @Test
    void snapshotIsReplacedOnlyAfterCommit() {
        transactions.executeWithoutResult(status -> {
            changeFeature.change(billingChange(BillingMode.MIRROR));
            assertThat(billingModeGate.currentMode()).isEqualTo(BillingMode.FULL);
        });

        assertThat(billingModeGate.currentMode()).isEqualTo(BillingMode.MIRROR);
    }

    @Test
    void rolledBackChangeLeavesSnapshotAndLogUntouched() {
        transactions.executeWithoutResult(status -> {
            changeFeature.change(billingChange(BillingMode.OFF));
            status.setRollbackOnly();
        });

        assertThat(billingModeGate.currentMode()).isEqualTo(BillingMode.FULL);
        assertThat(changeLogRepository.findAll()).isEmpty();
        assertThat(featureRepository.findById(FeatureCode.BILLING).orElseThrow().getMode()).isEqualTo(BillingMode.FULL);
    }

    @Test
    void snapshotRefreshesAfterTtlWhenAnotherReplicaChangedTheRow() {
        MutableClock.INSTANCE.setInstant(MutableClock.INSTANCE.instant());
        snapshotHolder.refresh();
        jdbc.update("update platform_feature set enabled = false, mode = 'OFF', version = version + 1 where code = 'BILLING'");

        MutableClock.INSTANCE.advance(Duration.ofSeconds(29));
        assertThat(billingModeGate.currentMode()).isEqualTo(BillingMode.FULL);

        MutableClock.INSTANCE.advance(Duration.ofSeconds(1));
        assertThat(billingModeGate.currentMode()).isEqualTo(BillingMode.OFF);
    }

    @Test
    void everyChangeWritesBeforeAndAfterWithActorAndReason() {
        FeatureState after = changeFeature.change(billingChange(BillingMode.MIRROR));

        List<FeatureChangeLog> log = changeLogRepository.findAll();
        assertThat(log).hasSize(1);
        FeatureChangeLog entry = log.get(0);
        assertThat(entry.getCode()).isEqualTo(FeatureCode.BILLING);
        assertThat(entry.getBeforeJson()).contains("\"mode\":\"FULL\"").contains("\"enabled\":true").contains("\"version\":0");
        assertThat(entry.getAfterJson()).contains("\"mode\":\"MIRROR\"").contains("\"enabled\":true").contains("\"version\":1");
        assertThat(entry.getActorPersonId()).isEqualTo(actorId);
        assertThat(entry.getActorKind()).isEqualTo(FeatureChangeActorKind.HUMAN);
        assertThat(entry.getReason()).isEqualTo("Instituto en prueba de espejo");
        assertThat(entry.getChangedAt()).isNotNull();
        assertThat(after.mode()).isEqualTo(BillingMode.MIRROR);
        assertThat(after.version()).isEqualTo(1);
    }

    @Test
    void changeOfAFeatureWithoutModeIsAuditedToo() {
        changeFeature.change(new FeatureChange(FeatureCode.PAYMENT_METHOD_GATEWAY, true, null, null, "Prende Mercado Pago", BillingModeChangeClearance.NONE));

        assertThat(featureQuery.isEnabled(FeatureCode.PAYMENT_METHOD_GATEWAY)).isTrue();
        FeatureChangeLog entry = changeLogRepository.findAll().get(0);
        assertThat(entry.getBeforeJson()).contains("\"enabled\":false").contains("\"mode\":null");
        assertThat(entry.getAfterJson()).contains("\"enabled\":true");
    }

    @Test
    void changeWithoutReasonIsRejectedAndNothingChanges() {
        for (String reason : new String[]{null, "", "    ", "abcd", "x".repeat(501)}) {
            FeatureChange change = new FeatureChange(FeatureCode.BILLING, null, BillingMode.MIRROR, null, reason, BillingModeChangeClearance.NONE);
            assertThatThrownBy(() -> changeFeature.change(change)).isInstanceOf(FeatureChangeReasonInvalidException.class);
        }
        assertThat(changeLogRepository.findAll()).isEmpty();
        assertThat(featureRepository.findById(FeatureCode.BILLING).orElseThrow().getMode()).isEqualTo(BillingMode.FULL);
    }

    @Test
    void reasonAtTheBoundsIsAccepted() {
        changeFeature.change(new FeatureChange(FeatureCode.BILLING, null, BillingMode.MIRROR, null, "abcde", new BillingModeChangeClearance(false, true)));
        changeFeature.change(new FeatureChange(FeatureCode.BILLING, null, BillingMode.OFF, null, "x".repeat(500), BillingModeChangeClearance.NONE));

        assertThat(changeLogRepository.findAll()).hasSize(2);
    }

    @Test
    void fullToOffWithoutPreflightIsRejectedAndNotLogged() {
        FeatureChange withoutPreflight = new FeatureChange(FeatureCode.BILLING, null, BillingMode.OFF, null, "Apagado sin verificación", BillingModeChangeClearance.NONE);

        assertThatThrownBy(() -> changeFeature.change(withoutPreflight))
                .isInstanceOf(InvalidBillingModeTransitionException.class);

        assertThat(changeLogRepository.findAll()).isEmpty();
        assertThat(billingModeGate.currentMode()).isEqualTo(BillingMode.FULL);
    }

    @Test
    void settingTheCurrentStateAgainIsRejected() {
        assertThatThrownBy(() -> changeFeature.change(billingChange(BillingMode.FULL)))
                .isInstanceOf(FeatureAlreadyInRequestedStateException.class);
    }

    @Test
    void staleExpectedVersionIsRejected() {
        FeatureChange change = new FeatureChange(FeatureCode.BILLING, null, BillingMode.MIRROR, 7L, "Cambio con versión vieja", new BillingModeChangeClearance(false, true));

        assertThatThrownBy(() -> changeFeature.change(change)).isInstanceOf(StaleFeatureVersionException.class);
    }

    @Test
    void matchingExpectedVersionIsAccepted() {
        changeFeature.change(new FeatureChange(FeatureCode.BILLING, null, BillingMode.MIRROR, 0L, "Cambio con versión vigente", new BillingModeChangeClearance(false, true)));

        assertThat(billingModeGate.currentMode()).isEqualTo(BillingMode.MIRROR);
    }

    @Test
    void invalidCombinationsOfEnabledAndModeAreRejected() {
        FeatureChange billingWithoutMode = new FeatureChange(FeatureCode.BILLING, true, null, null, "Sin modo", BillingModeChangeClearance.NONE);
        FeatureChange billingContradictingMode = new FeatureChange(FeatureCode.BILLING, true, BillingMode.OFF, null, "Enabled contradice", BillingModeChangeClearance.NONE);
        FeatureChange plainFeatureWithMode = new FeatureChange(FeatureCode.ANALYTICS, false, BillingMode.FULL, null, "Modo en feature simple", BillingModeChangeClearance.NONE);
        FeatureChange plainFeatureWithoutEnabled = new FeatureChange(FeatureCode.ANALYTICS, null, null, null, "Sin enabled", BillingModeChangeClearance.NONE);

        for (FeatureChange change : List.of(billingWithoutMode, billingContradictingMode, plainFeatureWithMode, plainFeatureWithoutEnabled)) {
            assertThatThrownBy(() -> changeFeature.change(change)).isInstanceOf(FeatureValueInvalidException.class);
        }
    }

    @Test
    void databaseRejectsABillingRowWhoseEnabledFlagContradictsItsMode() {
        assertThatThrownBy(() -> jdbc.update("update platform_feature set enabled = true, mode = 'OFF' where code = 'BILLING'"))
                .hasMessageContaining("ck_platform_feature_billing_enabled");
        assertThatThrownBy(() -> jdbc.update("update platform_feature set mode = 'FULL' where code = 'ANALYTICS'"))
                .hasMessageContaining("ck_platform_feature_billing_mode");
    }

    private FeatureChange billingChange(BillingMode mode) {
        return new FeatureChange(FeatureCode.BILLING, null, mode, null, "Instituto en prueba de espejo", new BillingModeChangeClearance(true, true));
    }

    private Long persistPlatformAdmin() {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        String suffix = UUID.randomUUID().toString();
        person.setUsername("platform.admin." + suffix);
        person.setEmail("platform.admin." + suffix + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        Role role = roleRepository.findByName("PLATFORM_ADMIN").orElseThrow();
        person.getRoles().add(role);
        entityManager.persist(person);
        entityManager.flush();
        return person.getId();
    }
}
