package com.red_escuela.backend.platform.service;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.platform.api.PaymentMethodAvailability;
import com.red_escuela.backend.platform.domain.InstituteSettingChange;
import com.red_escuela.backend.platform.domain.InstituteSettingState;
import com.red_escuela.backend.platform.entity.InstituteSettingChangeLog;
import com.red_escuela.backend.platform.exception.FeatureChangeReasonInvalidException;
import com.red_escuela.backend.platform.exception.InstituteSettingAlreadyInRequestedStateException;
import com.red_escuela.backend.platform.exception.LastDigitalPaymentMethodRequiredException;
import com.red_escuela.backend.platform.exception.StaleInstituteSettingVersionException;
import com.red_escuela.backend.platform.infrastructure.FeatureSnapshotHolder;
import com.red_escuela.backend.platform.infrastructure.InstituteSettingsSnapshotHolder;
import com.red_escuela.backend.platform.model.InstituteSettingCode;
import com.red_escuela.backend.platform.model.PaymentMethodCode;
import com.red_escuela.backend.platform.repository.InstituteSettingChangeLogRepository;
import com.red_escuela.backend.platform.repository.InstituteSettingRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.module.OnlinePaymentDependencyPort;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class InstituteSettingChangerIT extends AbstractIntegrationTest {

    @Autowired
    private InstituteSettingChanger changer;

    @Autowired
    private InstituteSettingRepository settingRepository;

    @Autowired
    private InstituteSettingChangeLogRepository changeLogRepository;

    @Autowired
    private PaymentMethodAvailability availability;

    @Autowired
    private InstituteSettingsSnapshotHolder instituteSnapshotHolder;

    @Autowired
    private FeatureSnapshotHolder featureSnapshotHolder;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private OnlinePaymentDependencyPort onlinePaymentDependencies;

    private Long actorId;

    @BeforeEach
    void authenticateAsDirector() {
        actorId = transactions.execute(status -> persistDirector().getId());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actorId.toString(), null));
        Mockito.when(onlinePaymentDependencies.countOpenOnlineDependencies()).thenReturn(0L);
        instituteSnapshotHolder.refresh();
        featureSnapshotHolder.refresh();
    }

    @AfterEach
    void restoreSeedState() {
        SecurityContextHolder.clearContext();
        jdbc.update("delete from institute_setting_change_log");
        jdbc.update("update institute_setting set enabled = true, version = 0, updated_by_id = null where code <> 'PAYMENT_METHOD_GATEWAY'");
        jdbc.update("update institute_setting set enabled = false, version = 0, updated_by_id = null where code = 'PAYMENT_METHOD_GATEWAY'");
        jdbc.update("update platform_feature set enabled = false, version = 0 where code = 'PAYMENT_METHOD_GATEWAY'");
        instituteSnapshotHolder.refresh();
        featureSnapshotHolder.refresh();
    }

    @Test
    void directorDisablesBankTransferAndAuditRowIsWritten() {
        assertThat(availability.isAvailable(PaymentMethodCode.BANK_TRANSFER)).isTrue();

        InstituteSettingState after = changer.change(change(InstituteSettingCode.PAYMENT_METHOD_BANK_TRANSFER, false, null, "El instituto solo cobra en efectivo"));

        assertThat(after.enabled()).isFalse();
        assertThat(after.version()).isEqualTo(1);
        assertThat(availability.isAvailable(PaymentMethodCode.BANK_TRANSFER)).isFalse();
        assertThat(availability.isAvailable(PaymentMethodCode.CASH)).isTrue();
        List<InstituteSettingChangeLog> log = changeLogRepository.findAll();
        assertThat(log).hasSize(1);
        InstituteSettingChangeLog entry = log.get(0);
        assertThat(entry.getCode()).isEqualTo(InstituteSettingCode.PAYMENT_METHOD_BANK_TRANSFER);
        assertThat(entry.getBeforeEnabled()).isTrue();
        assertThat(entry.isAfterEnabled()).isFalse();
        assertThat(entry.getActorPersonId()).isEqualTo(actorId);
        assertThat(entry.getReason()).isEqualTo("El instituto solo cobra en efectivo");
        assertThat(entry.getChangedAt()).isNotNull();
        assertThat(settingRepository.findById(InstituteSettingCode.PAYMENT_METHOD_BANK_TRANSFER).orElseThrow().getUpdatedById()).isEqualTo(actorId);
    }

    @Test
    void disablingLastDigitalMethodWithOpenOnlineInvoicesIsRejected() {
        Mockito.when(onlinePaymentDependencies.countOpenOnlineDependencies()).thenReturn(3L);

        assertThatThrownBy(() -> changer.change(change(InstituteSettingCode.PAYMENT_METHOD_BANK_TRANSFER, false, null, "Dejamos de aceptar transferencias")))
                .isInstanceOfSatisfying(LastDigitalPaymentMethodRequiredException.class, failure -> assertThat(failure.details()).containsEntry("openOnlineInvoices", 3L));

        assertThat(changeLogRepository.findAll()).isEmpty();
        assertThat(settingRepository.findById(InstituteSettingCode.PAYMENT_METHOD_BANK_TRANSFER).orElseThrow().isEnabled()).isTrue();
    }

    @Test
    void disablingLastDigitalMethodWithoutOpenOnlineInvoicesIsAllowed() {
        changer.change(change(InstituteSettingCode.PAYMENT_METHOD_BANK_TRANSFER, false, null, "No hay facturas online abiertas"));

        assertThat(settingRepository.findById(InstituteSettingCode.PAYMENT_METHOD_BANK_TRANSFER).orElseThrow().isEnabled()).isFalse();
    }

    @Test
    void disablingDigitalMethodIsAllowedWhenAnotherDigitalMethodIsEnabledByPlatformAndInstitute() {
        Mockito.when(onlinePaymentDependencies.countOpenOnlineDependencies()).thenReturn(5L);
        enableGatewayOnPlatformAndInstitute();

        changer.change(change(InstituteSettingCode.PAYMENT_METHOD_BANK_TRANSFER, false, null, "Queda la pasarela online"));

        assertThat(settingRepository.findById(InstituteSettingCode.PAYMENT_METHOD_BANK_TRANSFER).orElseThrow().isEnabled()).isFalse();
    }

    @Test
    void disablingDigitalMethodIsRejectedWhenTheOtherDigitalMethodIsOnlyEnabledByTheInstitute() {
        Mockito.when(onlinePaymentDependencies.countOpenOnlineDependencies()).thenReturn(5L);
        jdbc.update("update institute_setting set enabled = true where code = 'PAYMENT_METHOD_GATEWAY'");

        assertThatThrownBy(() -> changer.change(change(InstituteSettingCode.PAYMENT_METHOD_BANK_TRANSFER, false, null, "La pasarela está apagada por la plataforma")))
                .isInstanceOf(LastDigitalPaymentMethodRequiredException.class);
    }

    @Test
    void disablingTheGatewayIsRejectedWhenItIsTheLastUsableDigitalMethod() {
        Mockito.when(onlinePaymentDependencies.countOpenOnlineDependencies()).thenReturn(2L);
        enableGatewayOnPlatformAndInstitute();
        jdbc.update("update institute_setting set enabled = false where code = 'PAYMENT_METHOD_BANK_TRANSFER'");

        assertThatThrownBy(() -> changer.change(change(InstituteSettingCode.PAYMENT_METHOD_GATEWAY, false, null, "Dejamos la pasarela")))
                .isInstanceOf(LastDigitalPaymentMethodRequiredException.class);
    }

    @Test
    void disablingCashIsAllowedEvenWithOpenOnlineInvoices() {
        Mockito.when(onlinePaymentDependencies.countOpenOnlineDependencies()).thenReturn(7L);

        changer.change(change(InstituteSettingCode.PAYMENT_METHOD_CASH, false, null, "Ya no se cobra en caja"));

        assertThat(settingRepository.findById(InstituteSettingCode.PAYMENT_METHOD_CASH).orElseThrow().isEnabled()).isFalse();
    }

    @Test
    void enablingAMethodDisabledByThePlatformIsSavedButStaysInoperative() {
        InstituteSettingState after = changer.change(change(InstituteSettingCode.PAYMENT_METHOD_GATEWAY, true, null, "Queremos usar la pasarela"));

        assertThat(after.enabled()).isTrue();
        assertThat(availability.statusOfAll())
                .filteredOn(status -> status.code() == PaymentMethodCode.GATEWAY)
                .singleElement()
                .satisfies(status -> {
                    assertThat(status.platformEnabled()).isFalse();
                    assertThat(status.instituteEnabled()).isTrue();
                    assertThat(status.effective()).isFalse();
                });
    }

    @Test
    void staleVersionIsRejectedWithWhoAndWhen() {
        changer.change(change(InstituteSettingCode.PAYMENT_METHOD_CASH, false, 0L, "Primer cambio de efectivo"));

        assertThatThrownBy(() -> changer.change(change(InstituteSettingCode.PAYMENT_METHOD_CASH, true, 0L, "Segundo cambio con versión vieja")))
                .isInstanceOfSatisfying(StaleInstituteSettingVersionException.class, failure -> {
                    assertThat(failure.details()).containsKey("changedAt").containsEntry("changedById", actorId);
                    assertThat(failure.details()).containsKey("changedByName");
                });
        assertThat(changeLogRepository.findAll()).hasSize(1);
    }

    @Test
    void currentVersionIsAccepted() {
        changer.change(change(InstituteSettingCode.PAYMENT_METHOD_CASH, false, 0L, "Primer cambio de efectivo"));

        InstituteSettingState after = changer.change(change(InstituteSettingCode.PAYMENT_METHOD_CASH, true, 1L, "Volvemos a cobrar efectivo"));

        assertThat(after.version()).isEqualTo(2);
    }

    @Test
    void reasonShorterThanMinimumIsRejected() {
        for (String reason : new String[]{null, "", "    ", "abcd", "x".repeat(501)}) {
            assertThatThrownBy(() -> changer.change(change(InstituteSettingCode.PAYMENT_METHOD_CASH, false, null, reason)))
                    .isInstanceOf(FeatureChangeReasonInvalidException.class);
        }
        assertThat(changeLogRepository.findAll()).isEmpty();
        assertThat(settingRepository.findById(InstituteSettingCode.PAYMENT_METHOD_CASH).orElseThrow().isEnabled()).isTrue();
    }

    @Test
    void reasonAtTheBoundsIsAccepted() {
        changer.change(change(InstituteSettingCode.PAYMENT_METHOD_CASH, false, null, "abcde"));
        changer.change(change(InstituteSettingCode.PAYMENT_METHOD_CASH, true, null, "x".repeat(500)));

        assertThat(changeLogRepository.findAll()).hasSize(2);
    }

    @Test
    void changeToTheCurrentValueIsRejectedAndNotLogged() {
        assertThatThrownBy(() -> changer.change(change(InstituteSettingCode.PAYMENT_METHOD_CASH, true, null, "Sin cambio real")))
                .isInstanceOf(InstituteSettingAlreadyInRequestedStateException.class);

        assertThat(changeLogRepository.findAll()).isEmpty();
    }

    @Test
    void rolledBackChangeLeavesSnapshotAndLogUntouched() {
        transactions.executeWithoutResult(status -> {
            changer.change(change(InstituteSettingCode.PAYMENT_METHOD_CASH, false, null, "Cambio que se revierte"));
            status.setRollbackOnly();
        });

        assertThat(availability.isAvailable(PaymentMethodCode.CASH)).isTrue();
        assertThat(changeLogRepository.findAll()).isEmpty();
    }

    private void enableGatewayOnPlatformAndInstitute() {
        jdbc.update("update platform_feature set enabled = true where code = 'PAYMENT_METHOD_GATEWAY'");
        jdbc.update("update institute_setting set enabled = true where code = 'PAYMENT_METHOD_GATEWAY'");
        featureSnapshotHolder.refresh();
        instituteSnapshotHolder.refresh();
    }

    private InstituteSettingChange change(InstituteSettingCode code, boolean enabled, Long expectedVersion, String reason) {
        return new InstituteSettingChange(code, enabled, expectedVersion, reason);
    }

    private Person persistDirector() {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        String suffix = UUID.randomUUID().toString();
        person.setUsername("director." + suffix);
        person.setEmail("director." + suffix + "@example.com");
        person.setName("Diego");
        person.setLastname("Director");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        Role role = roleRepository.findByName("DIRECTOR").orElseThrow();
        person.getRoles().add(role);
        entityManager.persist(person);
        entityManager.flush();
        return person;
    }
}
