package com.red_escuela.backend.shared.event;

import com.red_escuela.backend.shared.resilience.CircuitBreakerFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.Clock;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.IncompleteEventPublications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class EventPublicationResubmissionJobToggleTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(IncompleteEventPublications.class, () -> mock(IncompleteEventPublications.class))
            .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
            .withBean(CircuitBreakerFactory.class, () -> new CircuitBreakerFactory(new MockEnvironment()
                    .withProperty("resilience.default.enabled", "false")
                    .withProperty("resilience.default.window", "5")
                    .withProperty("resilience.default.failure-threshold", "3")
                    .withProperty("resilience.default.wait-open", "30s"), Clock.systemUTC(), new SimpleMeterRegistry()))
            .withUserConfiguration(EventPublicationResubmissionJob.class);

    @Test
    void itIsRegisteredByDefault() {
        runner.run(context -> assertThat(context).hasSingleBean(EventPublicationResubmissionJob.class));
    }

    @Test
    void itIsNotRegisteredWhenDisabled() {
        runner.withPropertyValues("app.event-publication.resubmission.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(EventPublicationResubmissionJob.class));
    }
}
