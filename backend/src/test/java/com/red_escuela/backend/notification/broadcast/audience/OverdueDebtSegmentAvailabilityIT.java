package com.red_escuela.backend.notification.broadcast.audience;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.platform.infrastructure.FeatureSnapshotHolder;
import com.red_escuela.backend.notification.broadcast.audience.domain.SegmentKey;
import com.red_escuela.backend.notification.broadcast.audience.domain.SegmentQuantifier;
import com.red_escuela.backend.notification.broadcast.audience.dto.SegmentCriterionRequest;
import com.red_escuela.backend.notification.broadcast.audience.dto.SegmentSpecRequest;
import com.red_escuela.backend.notification.broadcast.audience.exception.SegmentNotAvailableException;
import com.red_escuela.backend.notification.broadcast.audience.specialist.SegmentResolver;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class OverdueDebtSegmentAvailabilityIT extends AbstractIntegrationTest {

    @Autowired
    private SegmentResolver segmentResolver;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

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
    void hiddenWhenBillingOff() {
        assertThat(segmentResolver.availableSegments()).extracting(descriptor -> descriptor.key()).doesNotContain(SegmentKey.OVERDUE_DEBT);
    }

    @Test
    void rejectsWithConflictWhenBillingOff() {
        SegmentSpecRequest spec = new SegmentSpecRequest(1, SegmentQuantifier.ALL, List.of(new SegmentCriterionRequest(SegmentKey.OVERDUE_DEBT, null)));

        assertThatThrownBy(() -> segmentResolver.resolvePersonIds(spec)).isInstanceOf(SegmentNotAvailableException.class);
    }

    @Test
    void previewAnswersConflictOverHttpWhenBillingIsOff() throws Exception {
        String token = new org.springframework.transaction.support.TransactionTemplate(transactionManager).execute(status -> {
            Person director = AudienceTestPersons.create(entityManager, roleRepository, "overdue-http-director-" + System.nanoTime(), true, null, "DIRECTOR");
            return "Bearer " + jwtService.generateAccessToken(director);
        });
        String body = "{\"channel\":\"EMAIL\",\"category\":\"INSTITUTIONAL\",\"segmentSpec\":{\"version\":1,\"quantifier\":\"ALL\","
                + "\"criteria\":[{\"key\":\"OVERDUE_DEBT\"}]},\"page\":0,\"size\":10}";

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/broadcasts/audience-preview")
                        .header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("SEGMENT_NOT_AVAILABLE"));
    }
}
