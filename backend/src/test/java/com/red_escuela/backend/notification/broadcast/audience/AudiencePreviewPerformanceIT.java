package com.red_escuela.backend.notification.broadcast.audience;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.notification.broadcast.audience.domain.ExclusionReason;
import com.red_escuela.backend.notification.broadcast.audience.domain.SegmentKey;
import com.red_escuela.backend.notification.broadcast.audience.domain.SegmentQuantifier;
import com.red_escuela.backend.notification.broadcast.audience.dto.AudiencePreviewRequest;
import com.red_escuela.backend.notification.broadcast.audience.dto.BroadcastAudiencePreviewResponse;
import com.red_escuela.backend.notification.broadcast.audience.dto.SegmentCriterionRequest;
import com.red_escuela.backend.notification.broadcast.audience.dto.SegmentSpecRequest;
import com.red_escuela.backend.notification.broadcast.audience.usecase.PreviewBroadcastAudienceUseCase;
import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.domain.ChannelType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AudiencePreviewPerformanceIT extends AbstractIntegrationTest {

    private static final int AUDIENCE_SIZE = 10_000;

    @Autowired
    private PreviewBroadcastAudienceUseCase previewBroadcastAudience;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private RoleRepository roleRepository;

    @Test
    void previewOfTenThousandPeopleWithMissingVariablesRunsUnderTwoSeconds() {
        Person anchor = AudienceTestPersons.create(entityManager, roleRepository, "perf-anchor", true, null, "PRECEPTOR");
        Long roleId = roleRepository.findByName("PRECEPTOR").orElseThrow().getId();
        entityManager.createNativeQuery("insert into address (street) select 'perf-addr-' || g from generate_series(1, "
                + AUDIENCE_SIZE + ") g").executeUpdate();
        entityManager.createNativeQuery("insert into person (name, last_name, email, email_verified, username, status, address_id) "
                + "select case when g % 10 = 0 then '' else 'Nombre' end, 'Apellido' || g, 'perf' || g || '@example.com', true, "
                + "'perf-user-' || g, 'ACTIVE', a.id from generate_series(1, " + AUDIENCE_SIZE
                + ") g join address a on a.street = 'perf-addr-' || g").executeUpdate();
        entityManager.createNativeQuery("insert into person_role (person_id, role_id) select id, " + roleId
                + " from person where username like 'perf-user-%'").executeUpdate();
        entityManager.flush();
        entityManager.clear();
        AudiencePreviewRequest request = new AudiencePreviewRequest(ChannelType.EMAIL, BroadcastCategory.INSTITUTIONAL,
                new SegmentSpecRequest(1, SegmentQuantifier.ALL,
                        List.of(new SegmentCriterionRequest(SegmentKey.STAFF_ROLE, "PRECEPTOR"))),
                true, null, "Hola", "Hola {{nombre}} {{apellido}}", 0, 50);

        previewBroadcastAudience.previewBroadcastAudience(request);
        long started = System.nanoTime();
        BroadcastAudiencePreviewResponse response = previewBroadcastAudience.previewBroadcastAudience(request);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - started);

        assertThat(response.total()).isGreaterThanOrEqualTo(AUDIENCE_SIZE);
        assertThat(response.excludedByReason().get(ExclusionReason.MISSING_TEMPLATE_VARIABLE))
                .isEqualTo(AUDIENCE_SIZE / 10);
        assertThat(anchor.getId()).isNotNull();
        assertThat(elapsed).isLessThan(Duration.ofSeconds(2));
    }
}
