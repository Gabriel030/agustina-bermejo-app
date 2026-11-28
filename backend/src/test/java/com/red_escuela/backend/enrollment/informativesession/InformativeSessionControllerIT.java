package com.red_escuela.backend.enrollment.informativesession;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class InformativeSessionControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private JwtService jwtService;
    private InformativeSessionFixtures fixtures;

    @BeforeEach
    void createFixtures() {
        fixtures = new InformativeSessionFixtures(transactionTemplate, entityManager, roleRepository, jwtService);
    }
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void preceptorCreatesUpdatesAndCancelsASession() throws Exception {
        String preceptor = fixtures.tokenOfNewPersonWithRole("PRECEPTOR");
        long id = createSession(preceptor, "2045-03-10T18:00:00", 10);

        mockMvc.perform(put("/informative-sessions/{id}", id).header("Authorization", "Bearer " + preceptor)
                        .contentType(MediaType.APPLICATION_JSON).content(body("Charla renovada", "2045-03-11T19:00:00", 20)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Charla renovada"))
                .andExpect(jsonPath("$.capacity").value(20));
        mockMvc.perform(post("/informative-sessions/{id}/cancel", id).header("Authorization", "Bearer " + preceptor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(post("/informative-sessions/{id}/cancel", id).header("Authorization", "Bearer " + preceptor))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INFORMATIVE_SESSION_NOT_SCHEDULED"));
    }

    @Test
    void directorCanManageSessions() throws Exception {
        String director = fixtures.tokenOfNewPersonWithRole("DIRECTOR");
        createSession(director, "2045-03-10T18:00:00", null);
    }

    @Test
    void studentAndAdministrativeCannotManageSessions() throws Exception {
        String preceptor = fixtures.tokenOfNewPersonWithRole("PRECEPTOR");
        long id = createSession(preceptor, "2045-03-10T18:00:00", 5);
        for (String role : new String[] {"ALUMNO", "ADMINISTRATIVO"}) {
            String token = fixtures.tokenOfNewPersonWithRole(role);
            mockMvc.perform(post("/informative-sessions").header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(body("Charla", "2045-03-10T18:00:00", 5)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(put("/informative-sessions/{id}", id).header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(body("Charla", "2045-03-10T18:00:00", 5)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/informative-sessions/{id}/cancel", id).header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void anonymousCannotListOrRegister() throws Exception {
        mockMvc.perform(get("/informative-sessions")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/informative-sessions/1/register")).andExpect(status().isUnauthorized());
    }

    @Test
    void registrationCountMatchesRowsAndMarksTheRegisteredPerson() throws Exception {
        String preceptor = fixtures.tokenOfNewPersonWithRole("PRECEPTOR");
        long id = createSession(preceptor, "2045-04-10T18:00:00", 5);
        String first = fixtures.tokenOfNewPersonWithRole("ALUMNO");
        String second = fixtures.tokenOfNewPersonWithRole("ALUMNO");

        register(id, first).andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationCount").value(1))
                .andExpect(jsonPath("$.registeredByMe").value(true));
        register(id, second).andExpect(status().isOk()).andExpect(jsonPath("$.registrationCount").value(2));

        assertThat(jdbc.queryForObject("select count(*) from informative_session_registration where session_id = ?", Integer.class, id)).isEqualTo(2);
        mockMvc.perform(get("/informative-sessions").header("Authorization", "Bearer " + first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id==" + id + ")].registrationCount").value(2))
                .andExpect(jsonPath("$[?(@.id==" + id + ")].registeredByMe").value(true));
        mockMvc.perform(get("/informative-sessions").header("Authorization", "Bearer " + preceptor))
                .andExpect(jsonPath("$[?(@.id==" + id + ")].registeredByMe").value(false));
    }

    @Test
    void registeringTwiceIsAConflict() throws Exception {
        String preceptor = fixtures.tokenOfNewPersonWithRole("PRECEPTOR");
        long id = createSession(preceptor, "2045-04-10T18:00:00", null);
        String student = fixtures.tokenOfNewPersonWithRole("ALUMNO");

        register(id, student).andExpect(status().isOk());
        register(id, student).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_REGISTERED_FOR_INFORMATIVE_SESSION"));
    }

    @Test
    void sessionWithoutCapacityNeverFills() throws Exception {
        String preceptor = fixtures.tokenOfNewPersonWithRole("PRECEPTOR");
        long id = createSession(preceptor, "2045-04-10T18:00:00", null);
        for (int i = 0; i < 3; i++) {
            register(id, fixtures.tokenOfNewPersonWithRole("ALUMNO")).andExpect(status().isOk());
        }
    }

    @Test
    void registeringInAFullSessionReturnsSessionFull() throws Exception {
        String preceptor = fixtures.tokenOfNewPersonWithRole("PRECEPTOR");
        long id = createSession(preceptor, "2045-04-10T18:00:00", 1);

        register(id, fixtures.tokenOfNewPersonWithRole("ALUMNO")).andExpect(status().isOk());
        register(id, fixtures.tokenOfNewPersonWithRole("ALUMNO")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_FULL"));
        assertThat(jdbc.queryForObject("select count(*) from informative_session_registration where session_id = ?", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void registeringInACancelledOrPastSessionIsUnprocessable() throws Exception {
        String preceptor = fixtures.tokenOfNewPersonWithRole("PRECEPTOR");
        String student = fixtures.tokenOfNewPersonWithRole("ALUMNO");
        long cancelled = createSession(preceptor, "2045-04-10T18:00:00", 5);
        mockMvc.perform(post("/informative-sessions/{id}/cancel", cancelled).header("Authorization", "Bearer " + preceptor))
                .andExpect(status().isOk());
        long past = createSession(preceptor, "2020-04-10T18:00:00", 5);

        register(cancelled, student).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INFORMATIVE_SESSION_NOT_SCHEDULED"));
        register(past, student).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INFORMATIVE_SESSION_ALREADY_STARTED"));
        register(999_999_999L, student).andExpect(status().isNotFound());
    }

    @Test
    void defaultListingExcludesCancelledAndPastSessions() throws Exception {
        String preceptor = fixtures.tokenOfNewPersonWithRole("PRECEPTOR");
        long upcoming = createSession(preceptor, "2045-05-10T18:00:00", 5);
        long cancelled = createSession(preceptor, "2045-05-11T18:00:00", 5);
        long past = createSession(preceptor, "2020-05-10T18:00:00", 5);
        mockMvc.perform(post("/informative-sessions/{id}/cancel", cancelled).header("Authorization", "Bearer " + preceptor))
                .andExpect(status().isOk());

        mockMvc.perform(get("/informative-sessions").header("Authorization", "Bearer " + preceptor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id==" + upcoming + ")]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.id==" + cancelled + ")]").isEmpty())
                .andExpect(jsonPath("$[?(@.id==" + past + ")]").isEmpty());
        mockMvc.perform(get("/informative-sessions").param("status", "CANCELLED").header("Authorization", "Bearer " + preceptor))
                .andExpect(jsonPath("$[?(@.id==" + cancelled + ")]").isNotEmpty());
        mockMvc.perform(get("/informative-sessions").param("from", "2020-01-01").header("Authorization", "Bearer " + preceptor))
                .andExpect(jsonPath("$[?(@.id==" + past + ")]").isNotEmpty());
    }

    @Test
    void capacityCannotDropBelowExistingRegistrations() throws Exception {
        String preceptor = fixtures.tokenOfNewPersonWithRole("PRECEPTOR");
        long id = createSession(preceptor, "2045-04-10T18:00:00", 5);
        register(id, fixtures.tokenOfNewPersonWithRole("ALUMNO")).andExpect(status().isOk());
        register(id, fixtures.tokenOfNewPersonWithRole("ALUMNO")).andExpect(status().isOk());

        mockMvc.perform(put("/informative-sessions/{id}", id).header("Authorization", "Bearer " + preceptor)
                        .contentType(MediaType.APPLICATION_JSON).content(body("Charla", "2045-04-10T18:00:00", 1)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INFORMATIVE_SESSION_CAPACITY_BELOW_REGISTRATIONS"));
    }

    @Test
    void relatedProgramMustExist() throws Exception {
        String preceptor = fixtures.tokenOfNewPersonWithRole("PRECEPTOR");
        mockMvc.perform(post("/informative-sessions").header("Authorization", "Bearer " + preceptor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Charla\",\"scheduledAt\":\"2045-03-10T18:00:00\",\"modality\":\"ONLINE\",\"relatedProgramId\":999999999}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidRequestIsRejected() throws Exception {
        String preceptor = fixtures.tokenOfNewPersonWithRole("PRECEPTOR");
        mockMvc.perform(post("/informative-sessions").header("Authorization", "Bearer " + preceptor)
                        .contentType(MediaType.APPLICATION_JSON).content(body(" ", "2045-03-10T18:00:00", 0)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void databaseRejectsValuesOutsideTheEnums() {
        assertThatThrownBy(() -> jdbc.update("insert into informative_session (title, scheduled_at, modality, status, created_by_id, created_at) "
                + "select 'x', now(), 'HYBRID', 'SCHEDULED', min(id), now() from person"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into informative_session (title, scheduled_at, modality, status, created_by_id, created_at) "
                + "select 'x', now(), 'ONLINE', 'DRAFT', min(id), now() from person"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private long createSession(String token, String scheduledAt, Integer capacity) throws Exception {
        String response = mockMvc.perform(post("/informative-sessions").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body("Charla informativa", scheduledAt, capacity)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.registrationCount").value(0))
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(response.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    private ResultActions register(long sessionId, String token) throws Exception {
        return mockMvc.perform(post("/informative-sessions/{id}/register", sessionId).header("Authorization", "Bearer " + token));
    }

    private static String body(String title, String scheduledAt, Integer capacity) {
        return "{\"title\":\"" + title + "\",\"scheduledAt\":\"" + scheduledAt + "\",\"modality\":\"ONLINE\","
                + "\"meetingUrl\":\"https://meet.example.com/charla\""
                + (capacity == null ? "" : ",\"capacity\":" + capacity) + "}";
    }
}
