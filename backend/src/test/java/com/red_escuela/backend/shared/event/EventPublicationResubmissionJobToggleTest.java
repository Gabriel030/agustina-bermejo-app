package com.red_escuela.backend.shared.event;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.IncompleteEventPublications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class EventPublicationResubmissionJobToggleTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(IncompleteEventPublications.class, () -> mock(IncompleteEventPublications.class))
            .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
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
