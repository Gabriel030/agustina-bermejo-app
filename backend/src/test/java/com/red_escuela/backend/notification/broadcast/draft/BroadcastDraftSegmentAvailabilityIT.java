package com.red_escuela.backend.notification.broadcast.draft;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.platform.infrastructure.FeatureSnapshotHolder;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class BroadcastDraftSegmentAvailabilityIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private FeatureSnapshotHolder snapshotHolder;

    @BeforeEach
    void switchBillingOff() {
        jdbc.update("update platform_feature set enabled = false, mode = 'OFF' where code = 'BILLING'");
        snapshotHolder.refresh();
    }

    @AfterEach
    void restoreBillingFull() {
        jdbc.update("update platform_feature set enabled = true, mode = 'FULL', version = 0 where code = 'BILLING'");
        snapshotHolder.refresh();
    }

    @Test
    void draftWithUnavailableSegmentAnswersConflict() throws Exception {
        String token = new TransactionTemplate(transactionManager).execute(status -> {
            Person director = AudienceFreezeFixtures.person(entityManager, roleRepository, "drf-seg-" + System.nanoTime(), true, "DIRECTOR");
            return "Bearer " + jwtService.generateAccessToken(director);
        });
        String body = "{\"name\":\"Aviso\",\"channel\":\"EMAIL\",\"category\":\"INSTITUTIONAL\",\"subject\":\"Hola\",\"bodyText\":\"Texto\","
                + "\"segmentSpec\":{\"version\":1,\"quantifier\":\"ALL\",\"criteria\":[{\"key\":\"OVERDUE_DEBT\"}]}}";

        mockMvc.perform(post("/broadcasts").header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEGMENT_NOT_AVAILABLE"));
    }
}
