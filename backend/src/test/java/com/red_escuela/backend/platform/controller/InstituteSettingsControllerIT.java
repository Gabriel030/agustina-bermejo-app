package com.red_escuela.backend.platform.controller;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.platform.infrastructure.FeatureSnapshotHolder;
import com.red_escuela.backend.platform.infrastructure.InstituteSettingsSnapshotHolder;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class InstituteSettingsControllerIT extends AbstractIntegrationTest {

    private static final String DISABLE_TRANSFER = "{\"enabled\":false,\"reason\":\"El instituto solo cobra en efectivo\"}";

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
    private InstituteSettingsSnapshotHolder instituteSnapshotHolder;

    @Autowired
    private FeatureSnapshotHolder featureSnapshotHolder;

    @Autowired
    private OnlinePaymentDependencyPort onlinePaymentDependencies;

    private String directorToken;
    private String administrativeToken;
    private String studentToken;
    private String platformAdminToken;

    @BeforeEach
    void createUsers() {
        restoreSeedState();
        directorToken = tokenOf("DIRECTOR", "Diego", "Director");
        administrativeToken = tokenOf("ADMINISTRATIVO", "Ana", "Administrativa");
        studentToken = tokenOf("ALUMNO", "Alma", "Alumna");
        platformAdminToken = tokenOf("PLATFORM_ADMIN", "Paula", "Plataforma");
        Mockito.doReturn(0L).when(onlinePaymentDependencies).countOpenOnlineDependencies();
        instituteSnapshotHolder.refresh();
        featureSnapshotHolder.refresh();
    }

    @AfterEach
    void restoreSeedState() {
        jdbc.update("delete from institute_setting_change_log");
        jdbc.update("update institute_setting set enabled = true, version = 0, updated_by_id = null where code <> 'PAYMENT_METHOD_GATEWAY'");
        jdbc.update("update institute_setting set enabled = false, version = 0, updated_by_id = null where code = 'PAYMENT_METHOD_GATEWAY'");
        instituteSnapshotHolder.refresh();
    }

    @Test
    void directorReadsAllSettingsWithPlatformAndInstituteFlags() throws Exception {
        mockMvc.perform(get("/institute-settings").header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentMethods.length()").value(3))
                .andExpect(jsonPath("$.paymentMethods[0].code").value("CASH"))
                .andExpect(jsonPath("$.paymentMethods[0].platformEnabled").value(true))
                .andExpect(jsonPath("$.paymentMethods[0].enabled").value(true))
                .andExpect(jsonPath("$.paymentMethods[0].effective").value(true))
                .andExpect(jsonPath("$.paymentMethods[2].code").value("GATEWAY"))
                .andExpect(jsonPath("$.paymentMethods[2].platformEnabled").value(false))
                .andExpect(jsonPath("$.paymentMethods[2].enabled").value(false))
                .andExpect(jsonPath("$.paymentMethods[2].effective").value(false))
                .andExpect(jsonPath("$.billingNotifications.enabled").value(true))
                .andExpect(jsonPath("$.billingNotifications.version").value(0));
    }

    @Test
    void administrativoReadsButCannotWrite() throws Exception {
        mockMvc.perform(get("/institute-settings").header("Authorization", "Bearer " + administrativeToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/institute-settings/audit").header("Authorization", "Bearer " + administrativeToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/institute-settings/payment-methods/BANK_TRANSFER").header("Authorization", "Bearer " + administrativeToken)
                        .contentType(MediaType.APPLICATION_JSON).content(DISABLE_TRANSFER))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/institute-settings/billing-notifications").header("Authorization", "Bearer " + administrativeToken)
                        .contentType(MediaType.APPLICATION_JSON).content(DISABLE_TRANSFER))
                .andExpect(status().isForbidden());

        Integer changes = jdbc.queryForObject("select count(*) from institute_setting_change_log", Integer.class);
        org.assertj.core.api.Assertions.assertThat(changes).isZero();
    }

    @Test
    void studentCannotReadOrWriteInstituteSettings() throws Exception {
        mockMvc.perform(get("/institute-settings").header("Authorization", "Bearer " + studentToken)).andExpect(status().isForbidden());
        mockMvc.perform(put("/institute-settings/payment-methods/CASH").header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON).content(DISABLE_TRANSFER))
                .andExpect(status().isForbidden());
    }

    @Test
    void platformAdminGets403OnInstituteSettings() throws Exception {
        mockMvc.perform(get("/institute-settings").header("Authorization", "Bearer " + platformAdminToken)).andExpect(status().isForbidden());
        mockMvc.perform(get("/institute-settings/audit").header("Authorization", "Bearer " + platformAdminToken)).andExpect(status().isForbidden());
        mockMvc.perform(get("/me/payment-methods").header("Authorization", "Bearer " + platformAdminToken)).andExpect(status().isForbidden());
        mockMvc.perform(put("/institute-settings/payment-methods/CASH").header("Authorization", "Bearer " + platformAdminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(DISABLE_TRANSFER))
                .andExpect(status().isForbidden());
    }

    @Test
    void directorDisablesTransferAndAuditShowsWhoAndWhy() throws Exception {
        mockMvc.perform(put("/institute-settings/payment-methods/BANK_TRANSFER").header("Authorization", "Bearer " + directorToken)
                        .contentType(MediaType.APPLICATION_JSON).content(DISABLE_TRANSFER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("BANK_TRANSFER"))
                .andExpect(jsonPath("$.platformEnabled").value(true))
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.effective").value(false))
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(get("/institute-settings/audit").header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].code").value("PAYMENT_METHOD_BANK_TRANSFER"))
                .andExpect(jsonPath("$.content[0].before").value(true))
                .andExpect(jsonPath("$.content[0].after").value(false))
                .andExpect(jsonPath("$.content[0].actor.fullName").value("Diego Director"))
                .andExpect(jsonPath("$.content[0].reason").value("El instituto solo cobra en efectivo"));
    }

    @Test
    void directorDisablesBillingNotifications() throws Exception {
        mockMvc.perform(put("/institute-settings/billing-notifications").header("Authorization", "Bearer " + directorToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false,\"reason\":\"Sin avisos de cobranza\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.effective").value(false))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void staleIfMatchReturns409WithWhoAndWhen() throws Exception {
        mockMvc.perform(put("/institute-settings/payment-methods/CASH").header("Authorization", "Bearer " + directorToken).header("If-Match", "0")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false,\"reason\":\"Primer cambio de efectivo\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/institute-settings/payment-methods/CASH").header("Authorization", "Bearer " + directorToken).header("If-Match", "0")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true,\"reason\":\"Segundo cambio con versión vieja\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_INSTITUTE_SETTING_VERSION"))
                .andExpect(jsonPath("$.details.changedByName").value("Diego Director"))
                .andExpect(jsonPath("$.details.changedAt").exists());
    }

    @Test
    void lastDigitalMethodWithOpenOnlineInvoicesReturns409() throws Exception {
        Mockito.doReturn(4L).when(onlinePaymentDependencies).countOpenOnlineDependencies();

        mockMvc.perform(put("/institute-settings/payment-methods/BANK_TRANSFER").header("Authorization", "Bearer " + directorToken)
                        .contentType(MediaType.APPLICATION_JSON).content(DISABLE_TRANSFER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_DIGITAL_PAYMENT_METHOD_REQUIRED"))
                .andExpect(jsonPath("$.details.openOnlineInvoices").value(4));
    }

    @Test
    void invalidBodyReturns400() throws Exception {
        mockMvc.perform(put("/institute-settings/payment-methods/CASH").header("Authorization", "Bearer " + directorToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Falta el campo enabled\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/institute-settings/payment-methods/CASH").header("Authorization", "Bearer " + directorToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false,\"reason\":\"abcd\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void alumnoGetsMyPaymentMethodsFromEffectiveState() throws Exception {
        mockMvc.perform(put("/institute-settings/payment-methods/BANK_TRANSFER").header("Authorization", "Bearer " + directorToken)
                        .contentType(MediaType.APPLICATION_JSON).content(DISABLE_TRANSFER))
                .andExpect(status().isOk());

        mockMvc.perform(get("/me/payment-methods").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].code").value("CASH"))
                .andExpect(jsonPath("$[0].available").value(true))
                .andExpect(jsonPath("$[1].code").value("BANK_TRANSFER"))
                .andExpect(jsonPath("$[1].available").value(false))
                .andExpect(jsonPath("$[2].code").value("GATEWAY"))
                .andExpect(jsonPath("$[2].available").value(false));
    }

    private String tokenOf(String roleName, String name, String lastname) {
        return transactions.execute(status -> jwtService.generateAccessToken(persistPerson(roleName, name, lastname)));
    }

    private Person persistPerson(String roleName, String name, String lastname) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        String suffix = UUID.randomUUID().toString();
        person.setUsername(roleName.toLowerCase() + "." + suffix);
        person.setEmail(roleName.toLowerCase() + "." + suffix + "@example.com");
        person.setName(name);
        person.setLastname(lastname);
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        Role role = roleRepository.findByName(roleName).orElseThrow();
        person.getRoles().add(role);
        entityManager.persist(person);
        entityManager.flush();
        return person;
    }
}
