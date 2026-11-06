package com.red_escuela.backend.platform.controller;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.platform.infrastructure.FeatureSnapshotHolder;
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

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class PlatformFeatureReadIT extends AbstractIntegrationTest {

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

    private String adminToken;
    private String directorToken;

    @BeforeEach
    void createUsers() {
        adminToken = transactions.execute(status -> jwtService.generateAccessToken(persistPerson("PLATFORM_ADMIN", "Paula", "Plataforma")));
        directorToken = transactions.execute(status -> jwtService.generateAccessToken(persistPerson("DIRECTOR", "Diego", "Director")));
    }

    @AfterEach
    void restoreSeedState() {
        jdbc.update("update platform_feature set enabled = true, version = 0, updated_by_id = null where code = 'ANALYTICS'");
        jdbc.update("delete from feature_change_log");
        snapshotHolder.refresh();
    }

    @Test
    void listFeaturesReturnsEightFeaturesWithVersionAndLastReason() throws Exception {
        changeAnalytics(false, "Apagamos analítica por mantenimiento", null);

        mockMvc.perform(get("/platform/features").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(8))
                .andExpect(jsonPath("$[0].code").value("BILLING"))
                .andExpect(jsonPath("$[1].code").value("FISCAL_INVOICING"))
                .andExpect(jsonPath("$[2].code").value("ANALYTICS"))
                .andExpect(jsonPath("$[3].code").value("PAYMENT_METHOD_CASH"))
                .andExpect(jsonPath("$[4].code").value("PAYMENT_METHOD_BANK_TRANSFER"))
                .andExpect(jsonPath("$[5].code").value("PAYMENT_METHOD_GATEWAY"))
                .andExpect(jsonPath("$[6].code").value("BILLING_NOTIFICATIONS"))
                .andExpect(jsonPath("$[7].code").value("EXTERNAL_BILLING"))
                .andExpect(jsonPath("$[2].enabled").value(false))
                .andExpect(jsonPath("$[2].version").value(1))
                .andExpect(jsonPath("$[2].lastReason").value("Apagamos analítica por mantenimiento"))
                .andExpect(jsonPath("$[2].updatedBy.fullName").value("Paula Plataforma"))
                .andExpect(jsonPath("$[0].version").value(0))
                .andExpect(jsonPath("$[0].lastReason").doesNotExist());
    }

    @Test
    void listChangesIsPagedNewestFirstAndFiltersByCode() throws Exception {
        changeAnalytics(false, "Primer cambio de analítica", null);
        changeAnalytics(true, "Segundo cambio de analítica", 1L);
        mockMvc.perform(put("/platform/features/FISCAL_INVOICING")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"reason\":\"Prendemos facturación fiscal\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/platform/features/changes").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].code").value("FISCAL_INVOICING"))
                .andExpect(jsonPath("$.content[0].actor.fullName").value("Paula Plataforma"))
                .andExpect(jsonPath("$.content[0].before.enabled").value(false))
                .andExpect(jsonPath("$.content[0].after.enabled").value(true));
        mockMvc.perform(get("/platform/features/changes").param("code", "ANALYTICS").param("size", "1")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].reason").value("Segundo cambio de analítica"));
    }

    @Test
    void staleVersionConflictReportsWhoChangedAndWhen() throws Exception {
        changeAnalytics(false, "Apagamos analítica por mantenimiento", null);

        mockMvc.perform(put("/platform/features/ANALYTICS")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("If-Match", 0)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"reason\":\"Cambio con versión vieja\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_FEATURE_VERSION"))
                .andExpect(jsonPath("$.details.changedByName").value("Paula Plataforma"))
                .andExpect(jsonPath("$.details.changedById").isNumber())
                .andExpect(jsonPath("$.details.changedAt").isString());
    }

    @Test
    void listChangesFiltersByActorKindAndDateRange() throws Exception {
        changeAnalytics(false, "Primer cambio de analítica", null);
        changeAnalytics(true, "Segundo cambio de analítica", 1L);
        Long actorId = jdbc.queryForObject("select actor_person_id from feature_change_log limit 1", Long.class);
        jdbc.update("update feature_change_log set changed_at = '2026-01-10 10:00:00' where reason = 'Primer cambio de analítica'");
        jdbc.update("update feature_change_log set actor_kind = 'SERVICE' where reason = 'Primer cambio de analítica'");

        mockMvc.perform(get("/platform/features/changes").param("actorKind", "SERVICE").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].reason").value("Primer cambio de analítica"));
        mockMvc.perform(get("/platform/features/changes").param("from", "2026-01-10").param("to", "2026-01-10").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].reason").value("Primer cambio de analítica"));
        mockMvc.perform(get("/platform/features/changes").param("from", "2026-01-11").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].reason").value("Segundo cambio de analítica"));
        mockMvc.perform(get("/platform/features/changes").param("actorPersonId", actorId.toString()).header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get("/platform/features/changes").param("actorPersonId", String.valueOf(actorId + 999)).header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void directorCannotReadPlatformFeatures() throws Exception {
        mockMvc.perform(get("/platform/features").header("Authorization", "Bearer " + directorToken)).andExpect(status().isForbidden());
        mockMvc.perform(get("/platform/features/changes").header("Authorization", "Bearer " + directorToken)).andExpect(status().isForbidden());
    }

    private void changeAnalytics(boolean enabled, String reason, Long expectedVersion) throws Exception {
        var request = put("/platform/features/ANALYTICS")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":" + enabled + ",\"reason\":\"" + reason + "\"}");
        if (expectedVersion != null) {
            request = request.header("If-Match", expectedVersion);
        }
        mockMvc.perform(request).andExpect(status().isOk());
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
