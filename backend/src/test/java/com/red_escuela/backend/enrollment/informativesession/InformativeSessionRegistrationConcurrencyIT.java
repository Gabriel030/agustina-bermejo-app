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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class InformativeSessionRegistrationConcurrencyIT extends AbstractIntegrationTest {

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
    void twoSimultaneousRegistrationsForTheLastSeatLeaveExactlyOneRegistration() throws Exception {
        String preceptor = fixtures.tokenOfNewPersonWithRole("PRECEPTOR");
        String created = mockMvc.perform(post("/informative-sessions").header("Authorization", "Bearer " + preceptor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Última plaza\",\"scheduledAt\":\"2045-06-10T18:00:00\",\"modality\":\"ONLINE\",\"capacity\":1}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long sessionId = Long.parseLong(created.replaceAll(".*\"id\":(\\d+).*", "$1"));
        int attempts = 2;
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            tokens.add(fixtures.tokenOfNewPersonWithRole("ALUMNO"));
        }
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        List<Future<String>> results = new ArrayList<>();
        for (String token : tokens) {
            results.add(executor.submit(() -> {
                ready.countDown();
                go.await();
                var response = mockMvc.perform(post("/informative-sessions/{id}/register", sessionId)
                        .header("Authorization", "Bearer " + token)).andReturn().getResponse();
                return response.getStatus() == 200 ? "REGISTERED" : response.getStatus() + ":" + response.getContentAsString();
            }));
        }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        go.countDown();
        List<String> outcomes = new ArrayList<>();
        for (Future<String> result : results) {
            outcomes.add(result.get(30, TimeUnit.SECONDS));
        }
        executor.shutdown();

        assertThat(outcomes).contains("REGISTERED");
        assertThat(outcomes.stream().filter(outcome -> !outcome.equals("REGISTERED")).toList())
                .singleElement().asString().startsWith("409:").contains("SESSION_FULL");
        assertThat(jdbc.queryForObject("select count(*) from informative_session_registration where session_id = ?", Integer.class, sessionId))
                .isEqualTo(1);
    }
}
