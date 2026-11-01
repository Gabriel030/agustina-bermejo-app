package com.red_escuela.backend.platform.controller;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.platform.infrastructure.FeatureSnapshotHolder;
import com.red_escuela.backend.platform.model.BillingMode;
import com.red_escuela.backend.platform.model.FeatureCode;
import com.red_escuela.backend.platform.repository.FeatureChangeLogRepository;
import com.red_escuela.backend.platform.repository.PlatformFeatureRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class PlatformModulesControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private FeatureSnapshotHolder snapshotHolder;

    @Autowired
    private FeatureChangeLogRepository changeLogRepository;

    @Autowired
    private PlatformFeatureRepository featureRepository;

    private Map<String, String> tokensByRole;

    @BeforeEach
    void createOneUserPerRole() {
        tokensByRole = transactions.execute(status -> {
            Map<String, String> tokens = new java.util.LinkedHashMap<>();
            for (String role : new String[] {"ALUMNO", "DOCENTE", "PRECEPTOR", "ADMINISTRATIVO", "DIRECTOR", "PLATFORM_ADMIN"}) {
                tokens.put(role, jwtService.generateAccessToken(persistPerson(role)));
            }
            return tokens;
        });
    }

    @AfterEach
    void restoreSeedState() {
        jdbc.update("update platform_feature set enabled = true, mode = 'FULL', version = 0 where code = 'BILLING'");
        jdbc.update("delete from feature_change_log");
        snapshotHolder.refresh();
    }

    @Test
    void meModulesReturnsTheEffectiveValueForEachRole() throws Exception {
        for (Map.Entry<String, String> entry : tokensByRole.entrySet()) {
            mockMvc.perform(get("/me/modules").header("Authorization", "Bearer " + entry.getValue()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.billing.mode").value("FULL"))
                    .andExpect(jsonPath("$.fiscalInvoicing.enabled").value(false))
                    .andExpect(jsonPath("$.analytics.enabled").value(true))
                    .andExpect(jsonPath("$.paymentMethods[?(@.code=='CASH')].available").value(true))
                    .andExpect(jsonPath("$.paymentMethods[?(@.code=='GATEWAY')].available").value(false));
        }
    }

    @Test
    void meModulesCombinesPlatformAndBillingModeSoOffHidesEveryBillingDependentFlag() throws Exception {
        jdbc.update("update platform_feature set enabled = false, mode = 'OFF' where code = 'BILLING'");
        snapshotHolder.refresh();

        mockMvc.perform(get("/me/modules").header("Authorization", "Bearer " + tokensByRole.get("ALUMNO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.billing.mode").value("OFF"))
                .andExpect(jsonPath("$.paymentMethods[?(@.code=='CASH')].available").value(false))
                .andExpect(jsonPath("$.paymentMethods[?(@.code=='BANK_TRANSFER')].available").value(false))
                .andExpect(jsonPath("$.billingNotifications.enabled").value(false))
                .andExpect(jsonPath("$.analytics.enabled").value(true));
    }

    @Test
    void meModulesRequiresASession() throws Exception {
        mockMvc.perform(get("/me/modules")).andExpect(status().isForbidden());
    }

    @Test
    void publicModulesIsReachableWithoutSessionAndExposesOnlyTheBillingFlag() throws Exception {
        mockMvc.perform(get("/public/modules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.billingEnabled").value(true))
                .andExpect(jsonPath("$.length()").value(1));

        jdbc.update("update platform_feature set enabled = false, mode = 'OFF' where code = 'BILLING'");
        snapshotHolder.refresh();

        mockMvc.perform(get("/public/modules")).andExpect(jsonPath("$.billingEnabled").value(false));
    }

    @Test
    void platformAdminMovesBillingToMirrorWithReasonAndTheChangeIsAudited() throws Exception {
        startFromBillingOff();
        mockMvc.perform(put("/platform/features/BILLING")
                        .header("Authorization", "Bearer " + tokensByRole.get("PLATFORM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"MIRROR\",\"reason\":\"Pasamos a espejo para la migración\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("BILLING"))
                .andExpect(jsonPath("$.mode").value("MIRROR"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.version").value(1));

        assertThat(featureRepository.findById(FeatureCode.BILLING).orElseThrow().getMode()).isEqualTo(BillingMode.MIRROR);
        assertThat(changeLogRepository.findAll()).singleElement().satisfies(entry -> {
            assertThat(entry.getReason()).isEqualTo("Pasamos a espejo para la migración");
            assertThat(entry.getBeforeJson()).contains("OFF");
            assertThat(entry.getAfterJson()).contains("MIRROR");
        });
    }

    @Test
    void directorAndEveryOtherRoleAreForbiddenFromChangingFeatures() throws Exception {
        for (String role : new String[] {"ALUMNO", "DOCENTE", "PRECEPTOR", "ADMINISTRATIVO", "DIRECTOR"}) {
            mockMvc.perform(put("/platform/features/BILLING")
                            .header("Authorization", "Bearer " + tokensByRole.get(role))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"mode\":\"OFF\",\"reason\":\"Intento no autorizado\"}"))
                    .andExpect(status().isForbidden());
        }

        assertThat(featureRepository.findById(FeatureCode.BILLING).orElseThrow().getMode()).isEqualTo(BillingMode.FULL);
        assertThat(changeLogRepository.findAll()).isEmpty();
    }

    @Test
    void changeWithoutAValidReasonIsRejectedAndNothingIsWritten() throws Exception {
        mockMvc.perform(put("/platform/features/BILLING")
                        .header("Authorization", "Bearer " + tokensByRole.get("PLATFORM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"MIRROR\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/platform/features/BILLING")
                        .header("Authorization", "Bearer " + tokensByRole.get("PLATFORM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"MIRROR\",\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest());

        assertThat(changeLogRepository.findAll()).isEmpty();
    }

    @Test
    void leavingFullWithoutPreflightIsAConflict() throws Exception {
        mockMvc.perform(put("/platform/features/BILLING")
                        .header("Authorization", "Bearer " + tokensByRole.get("PLATFORM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"OFF\",\"reason\":\"Apagar facturación\"}"))
                .andExpect(status().isConflict());

        assertThat(featureRepository.findById(FeatureCode.BILLING).orElseThrow().getMode()).isEqualTo(BillingMode.FULL);
    }

    @Test
    void aStaleIfMatchVersionIsRejectedAndTheCurrentOneIsAccepted() throws Exception {
        startFromBillingOff();
        String body = "{\"mode\":\"MIRROR\",\"reason\":\"Pasar a espejo\"}";

        mockMvc.perform(put("/platform/features/BILLING")
                        .header("Authorization", "Bearer " + tokensByRole.get("PLATFORM_ADMIN"))
                        .header("If-Match", "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
        assertThat(featureRepository.findById(FeatureCode.BILLING).orElseThrow().getMode()).isEqualTo(BillingMode.OFF);

        mockMvc.perform(put("/platform/features/BILLING")
                        .header("Authorization", "Bearer " + tokensByRole.get("PLATFORM_ADMIN"))
                        .header("If-Match", "0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void previewReportsTheRealBillingImpactWithoutChangingAnything() throws Exception {
        long openInvoices = jdbc.queryForObject("select count(*) from invoice where document_status = 'ISSUED' and payment_status in ('PENDING','OVERDUE','PAYMENT_UNDER_REVIEW')", Long.class);
        long drafts = jdbc.queryForObject("select count(*) from invoice where document_status = 'DRAFT'", Long.class);
        long pendingPayments = jdbc.queryForObject("select count(*) from payment where status in ('PENDING','AUTHORIZED')", Long.class);

        mockMvc.perform(get("/platform/features/BILLING/preview").param("target", "OFF")
                        .header("Authorization", "Bearer " + tokensByRole.get("PLATFORM_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("BILLING"))
                .andExpect(jsonPath("$.currentMode").value("FULL"))
                .andExpect(jsonPath("$.targetMode").value("OFF"))
                .andExpect(jsonPath("$.currentVersion").value(0))
                .andExpect(jsonPath("$.impact.openInvoices").value(openInvoices))
                .andExpect(jsonPath("$.impact.pendingDraftInvoices").value(drafts))
                .andExpect(jsonPath("$.impact.paymentsPendingReconciliation").value(pendingPayments))
                .andExpect(jsonPath("$.impact.scheduledJobs.length()").value(12))
                .andExpect(jsonPath("$.confirmationToken").isNotEmpty())
                .andExpect(jsonPath("$.confirmationExpiresAt").isNotEmpty());

        assertThat(featureRepository.findById(FeatureCode.BILLING).orElseThrow().getMode()).isEqualTo(BillingMode.FULL);
        assertThat(changeLogRepository.findAll()).isEmpty();
    }

    @Test
    void previewIsForbiddenForEveryRoleExceptPlatformAdmin() throws Exception {
        for (String role : new String[] {"ALUMNO", "DOCENTE", "PRECEPTOR", "ADMINISTRATIVO", "DIRECTOR"}) {
            mockMvc.perform(get("/platform/features/BILLING/preview").param("target", "OFF")
                            .header("Authorization", "Bearer " + tokensByRole.get(role)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/platform/features/BILLING/preview").param("target", "OFF")).andExpect(status().isForbidden());
    }

    @Test
    void previewOfAFeatureWithoutModeIsABadRequestAndOfTheCurrentModeAConflict() throws Exception {
        mockMvc.perform(get("/platform/features/ANALYTICS/preview").param("target", "OFF")
                        .header("Authorization", "Bearer " + tokensByRole.get("PLATFORM_ADMIN")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/platform/features/BILLING/preview").param("target", "FULL")
                        .header("Authorization", "Bearer " + tokensByRole.get("PLATFORM_ADMIN")))
                .andExpect(status().isConflict());
    }

    @Test
    void fullToOffWithTheConfirmationOfItsPreviewIsAppliedAndAuditedWithTheReason() throws Exception {
        String confirmation = previewConfirmation("OFF");

        mockMvc.perform(put("/platform/features/BILLING")
                        .header("Authorization", "Bearer " + tokensByRole.get("PLATFORM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"OFF\",\"reason\":\"Apagamos facturación para la prueba\",\"previewConfirmationToken\":\"" + confirmation + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("OFF"))
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.version").value(1));

        assertThat(featureRepository.findById(FeatureCode.BILLING).orElseThrow().getMode()).isEqualTo(BillingMode.OFF);
        assertThat(changeLogRepository.findAll()).singleElement().satisfies(entry -> {
            assertThat(entry.getReason()).isEqualTo("Apagamos facturación para la prueba");
            assertThat(entry.getBeforeJson()).contains("FULL");
            assertThat(entry.getAfterJson()).contains("OFF");
        });
    }

    @Test
    void fullToMirrorIsAlsoAllowedWithItsOwnConfirmation() throws Exception {
        String confirmation = previewConfirmation("MIRROR");

        mockMvc.perform(put("/platform/features/BILLING")
                        .header("Authorization", "Bearer " + tokensByRole.get("PLATFORM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"MIRROR\",\"reason\":\"Pasamos a espejo\",\"previewConfirmationToken\":\"" + confirmation + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("MIRROR"));
    }

    @Test
    void aConfirmationIssuedForAnotherTargetOrTamperedWithIsRejectedAndNothingIsWritten() throws Exception {
        String confirmationForMirror = previewConfirmation("MIRROR");
        String tampered = confirmationForMirror.substring(0, confirmationForMirror.length() - 2) + "AA";

        for (String token : new String[] {confirmationForMirror, tampered, "garbage"}) {
            mockMvc.perform(put("/platform/features/BILLING")
                            .header("Authorization", "Bearer " + tokensByRole.get("PLATFORM_ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"mode\":\"OFF\",\"reason\":\"Apagar con confirmación ajena\",\"previewConfirmationToken\":\"" + token + "\"}"))
                    .andExpect(status().isConflict());
        }

        assertThat(featureRepository.findById(FeatureCode.BILLING).orElseThrow().getMode()).isEqualTo(BillingMode.FULL);
        assertThat(changeLogRepository.findAll()).isEmpty();
    }

    @Test
    void aValidConfirmationDoesNotExemptTheReasonNorTheRole() throws Exception {
        String confirmation = previewConfirmation("OFF");

        mockMvc.perform(put("/platform/features/BILLING")
                        .header("Authorization", "Bearer " + tokensByRole.get("PLATFORM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"OFF\",\"previewConfirmationToken\":\"" + confirmation + "\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/platform/features/BILLING")
                        .header("Authorization", "Bearer " + tokensByRole.get("DIRECTOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"OFF\",\"reason\":\"El director intenta apagar\",\"previewConfirmationToken\":\"" + confirmation + "\"}"))
                .andExpect(status().isForbidden());

        assertThat(featureRepository.findById(FeatureCode.BILLING).orElseThrow().getMode()).isEqualTo(BillingMode.FULL);
        assertThat(changeLogRepository.findAll()).isEmpty();
    }

    private String previewConfirmation(String target) throws Exception {
        String body = mockMvc.perform(get("/platform/features/BILLING/preview").param("target", target)
                        .header("Authorization", "Bearer " + tokensByRole.get("PLATFORM_ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.confirmationToken");
    }

    private void startFromBillingOff() {
        jdbc.update("update platform_feature set enabled = false, mode = 'OFF' where code = 'BILLING'");
        snapshotHolder.refresh();
    }

    private Person persistPerson(String roleName) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        String suffix = UUID.randomUUID().toString();
        person.setUsername(roleName.toLowerCase() + "." + suffix);
        person.setEmail(roleName.toLowerCase() + "." + suffix + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        Role role = roleRepository.findByName(roleName).orElseThrow();
        person.getRoles().add(role);
        entityManager.persist(person);
        entityManager.flush();
        return person;
    }
}
