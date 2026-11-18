package com.red_escuela.backend.security;

import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import com.red_escuela.backend.shared.audit.AuditLogEntry;
import com.red_escuela.backend.shared.audit.AuditLogRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuditLogEndpointIT extends AuthorizationMatrixSupport {

    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManagerFactory entityManagerFactory;
    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private Long directorId;
    private Long administrativeId;

    @BeforeEach
    void seedAuditEntries() {
        directorId = personId(MatrixRole.DIRECTOR);
        administrativeId = personId(MatrixRole.ADMINISTRATIVO);
        auditLogRepository.deleteAll();
        save(directorId, "DIRECTOR", LocalDateTime.of(2026, 11, 10, 9, 0), AuditAction.STAFF_WITHDRAWN, null, null);
        save(administrativeId, "ADMINISTRATIVO", LocalDateTime.of(2026, 11, 12, 0, 0), AuditAction.STAFF_CREATED, null, null);
        save(administrativeId, "ADMINISTRATIVO", LocalDateTime.of(2026, 11, 12, 23, 59), AuditAction.STAFF_REACTIVATED, null, null);
        save(administrativeId, "ADMINISTRATIVO", LocalDateTime.of(2026, 11, 13, 0, 0), AuditAction.STAFF_WITHDRAWN, null, null);
        save(null, null, LocalDateTime.of(2026, 11, 12, 12, 0), AuditAction.PASSWORD_RESET_COMPLETED, null, null);
        save(directorId, "DIRECTOR", LocalDateTime.of(2026, 11, 14, 8, 0), AuditAction.PASSWORD_CHANGED,
                "{\"passwordHash\":\"$2a$10$old\"}", "{\"passwordHash\":\"$2a$10$new\",\"status\":\"ACTIVE\"}");
    }

    @Test
    void filtersByActorRoleAndDateRange() throws Exception {
        mockMvc.perform(directorGet("/audit-log").param("actorRole", "ADMINISTRATIVO", "DIRECTOR")
                        .param("from", "2026-11-12").param("to", "2026-11-12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[0].action").value("STAFF_REACTIVATED"))
                .andExpect(jsonPath("$.items[1].action").value("STAFF_CREATED"));

        mockMvc.perform(directorGet("/audit-log").param("actorRole", "DIRECTOR"))
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(directorGet("/audit-log").param("actorPersonId", String.valueOf(administrativeId))
                        .param("from", "2026-11-13"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].action").value("STAFF_WITHDRAWN"));

        mockMvc.perform(directorGet("/audit-log").param("from", "2026-11-13").param("to", "2026-11-12"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void actorNameIsResolvedInOneQuery() throws Exception {
        long statementsForOneEntry = statementsFor(directorGet("/audit-log").param("size", "1"));
        long statementsForFullPage = statementsFor(directorGet("/audit-log").param("size", "6"));

        assertThat(statementsForFullPage).isEqualTo(statementsForOneEntry);

        mockMvc.perform(directorGet("/audit-log").param("actorPersonId", String.valueOf(directorId)))
                .andExpect(jsonPath("$.items[0].actorName").value("Matriz DIRECTOR"));
        mockMvc.perform(directorGet("/audit-log").param("action", "PASSWORD_RESET_COMPLETED"))
                .andExpect(jsonPath("$.items[0].actorName").value("Sistema"));
    }

    @Test
    void sensitiveFieldsAreMasked() throws Exception {
        MvcResult result = mockMvc.perform(directorGet("/audit-log").param("action", "PASSWORD_CHANGED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].afterValue").value(org.hamcrest.Matchers.containsString("\"status\":\"ACTIVE\"")))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("$2a$10$old", "$2a$10$new")
                .contains("***");
    }

    @Test
    void directorAndPlatformAdminAreIsolatedByEndpoint() throws Exception {
        mockMvc.perform(directorGet("/audit-log")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(6));
        mockMvc.perform(directorGet("/platform/audit-log")).andExpect(status().isForbidden());
        mockMvc.perform(get("/audit-log").header("Authorization", "Bearer " + fixtures.tokenFor(MatrixRole.PLATFORM_ADMIN)))
                .andExpect(status().isForbidden());
    }

    @Test
    void platformEndpointAppliesFiltersMasksValuesAndResolvesNames() throws Exception {
        String platformToken = fixtures.tokenFor(MatrixRole.PLATFORM_ADMIN);

        MvcResult result = mockMvc.perform(get("/platform/audit-log").header("Authorization", "Bearer " + platformToken)
                        .param("actorRole", "DIRECTOR").param("from", "2026-11-14").param("to", "2026-11-14"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].actorName").value("Matriz DIRECTOR"))
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("$2a$10$old", "$2a$10$new");

        mockMvc.perform(get("/platform/audit-log").header("Authorization", "Bearer " + platformToken)
                        .param("action", "PASSWORD_RESET_COMPLETED"))
                .andExpect(jsonPath("$.items[0].actorName").value("Sistema"));
    }

    @Test
    void unknownActorRoleReturnsAnEmptyPage() throws Exception {
        mockMvc.perform(directorGet("/audit-log").param("actorRole", "NOT_A_ROLE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void reasonIsMaskedWhenItCarriesSecrets() throws Exception {
        auditLogRepository.save(AuditLogEntry.builder().occurredAt(LocalDateTime.of(2026, 11, 15, 8, 0))
                .actorPersonId(directorId).actorRole("DIRECTOR").action(AuditAction.STAFF_CREATED)
                .entityType(AuditEntityType.PERSON).entityId(2L).reason("alta con password=Temp-4821").build());

        MvcResult result = mockMvc.perform(directorGet("/audit-log").param("action", "STAFF_CREATED").param("from", "2026-11-15"))
                .andExpect(status().isOk()).andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("Temp-4821");
    }

    @Test
    void unauthorizedRoleGets403() throws Exception {
        for (MatrixRole role : new MatrixRole[]{MatrixRole.ALUMNO, MatrixRole.DOCENTE, MatrixRole.PRECEPTOR, MatrixRole.ADMINISTRATIVO}) {
            mockMvc.perform(get("/audit-log").header("Authorization", "Bearer " + fixtures.tokenFor(role)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/platform/audit-log").header("Authorization", "Bearer " + fixtures.tokenFor(role)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/audit-log")).andExpect(status().is4xxClientError());
        mockMvc.perform(get("/platform/audit-log").header("Authorization", "Bearer " + fixtures.tokenFor(MatrixRole.PLATFORM_ADMIN)))
                .andExpect(status().isOk());
    }

    private MockHttpServletRequestBuilder directorGet(String path) {
        return get(path).header("Authorization", "Bearer " + fixtures.tokenFor(MatrixRole.DIRECTOR));
    }

    private long statementsFor(MockHttpServletRequestBuilder request) throws Exception {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        entityManager.flush();
        entityManager.clear();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        mockMvc.perform(request).andExpect(status().isOk());
        long count = statistics.getPrepareStatementCount();
        statistics.setStatisticsEnabled(false);
        return count;
    }

    private Long personId(MatrixRole role) {
        return jdbcTemplate.queryForObject("select id from person where username = ?", Long.class,
                "matrix." + role.name().toLowerCase());
    }

    private void save(Long actorId, String actorRole, LocalDateTime occurredAt, AuditAction action, String before, String after) {
        auditLogRepository.save(AuditLogEntry.builder().occurredAt(occurredAt).actorPersonId(actorId).actorRole(actorRole)
                .action(action).entityType(AuditEntityType.PERSON).entityId(1L).beforeValue(before).afterValue(after).build());
    }
}
