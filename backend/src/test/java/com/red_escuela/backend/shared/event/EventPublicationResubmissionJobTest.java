package com.red_escuela.backend.shared.event;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.IncompleteEventPublications;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventPublicationResubmissionJobTest {

    private final IncompleteEventPublications incompleteEventPublications = mock(IncompleteEventPublications.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final EventPublicationResubmissionJob job = new EventPublicationResubmissionJob(incompleteEventPublications, jdbcTemplate);

    @Test
    @Tag("D-178")
    void itResubmitsOnlyPublicationsOlderThanFiveMinutes() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);

        job.resubmitIncompletePublications();

        verify(incompleteEventPublications).resubmitIncompletePublicationsOlderThan(Duration.ofMinutes(5));
    }

    @Test
    @Tag("D-178")
    void itChecksForPublicationsStuckForMoreThanAnHour() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), anyLong())).thenReturn(2L);

        job.reportStuckPublications();

        verify(jdbcTemplate).queryForObject(anyString(), eq(Long.class), eq(3600L));
    }
}
