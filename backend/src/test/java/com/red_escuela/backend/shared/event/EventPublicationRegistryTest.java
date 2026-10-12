package com.red_escuela.backend.shared.event;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.AuthProvider;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.service.PasswordResetCodeIssuedEvent;
import com.red_escuela.backend.identity.usecase.auth.RequestPasswordResetUseCase;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.mail.EmailDeliveryException;
import com.red_escuela.backend.shared.mail.EmailService;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
@Import(EventPublicationRegistryTest.RecordingListenerConfiguration.class)
class EventPublicationRegistryTest extends AbstractIntegrationTest {

    record ProbeEvent(String correlationId, Instant occurredAt, String payload) implements DomainEvent {
    }

    static class RecordingListener {
        private final Queue<Map<String, String>> mdcSnapshots = new ConcurrentLinkedQueue<>();

        @ApplicationModuleListener
        public void onProbe(ProbeEvent event) {
            mdcSnapshots.add(MDC.getCopyOfContextMap());
        }

        public Queue<Map<String, String>> snapshots() {
            return mdcSnapshots;
        }
    }

    @TestConfiguration
    static class RecordingListenerConfiguration {
        @Bean
        RecordingListener recordingListener() {
            return new RecordingListener();
        }
    }

    @Autowired
    private RequestPasswordResetUseCase requestPasswordResetUseCase;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private IncompleteEventPublications incompleteEventPublications;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private RecordingListener recordingListener;
    @Autowired
    private MeterRegistry meterRegistry;
    @MockBean
    private EmailService emailService;

    private String email;
    private Long personId;
    private Long addressId;

    @BeforeEach
    void persistPerson() {
        jdbcTemplate.update("delete from event_publication");
        recordingListener.snapshots().clear();
        email = "registry-" + System.nanoTime() + "@example.com";
        transactionTemplate.executeWithoutResult(status -> {
            Address address = new Address();
            entityManager.persist(address);
            Person person = new Person();
            person.setUsername(email);
            person.setEmail(email);
            person.setAuthProvider(AuthProvider.LOCAL);
            person.setAddress(address);
            person.setStatus(StatusType.ACTIVE);
            entityManager.persist(person);
            personId = person.getId();
            addressId = address.getId();
        });
    }

    @AfterEach
    void cleanUp() {
        MDC.clear();
        jdbcTemplate.update("delete from event_publication");
        jdbcTemplate.update("delete from password_reset_token where person_id = ?", personId);
        jdbcTemplate.update("delete from person where id = ?", personId);
        jdbcTemplate.update("delete from address where id = ?", addressId);
    }

    private String personEventPattern() {
        return "\"personId\":" + personId + "[,}]";
    }

    private long incompletePublications() {
        return jdbcTemplate.queryForObject(
                "select count(*) from event_publication where completion_date is null and serialized_event ~ ?", Long.class, personEventPattern());
    }

    private long allPublications() {
        return jdbcTemplate.queryForObject("select count(*) from event_publication where serialized_event ~ ?", Long.class, personEventPattern());
    }

    private boolean belongsToThisPerson(EventPublication publication) {
        return publication.getEvent() instanceof PasswordResetCodeIssuedEvent event && personId.equals(event.personId());
    }

    @Test
    @Tag("D-178")
    @Tag("D-183")
    void aListenerFailureDoesNotRollBackTheBusinessTransactionAndTheRegistryRedeliversIt() {
        doThrow(new EmailDeliveryException("smtp down", new RuntimeException())).doNothing().when(emailService).sendPasswordResetCode(any(), anyString());

        transactionTemplate.executeWithoutResult(status -> requestPasswordResetUseCase.requestReset(email));

        verify(emailService, timeout(5000)).sendPasswordResetCode(any(Person.class), anyString());
        await().until(() -> incompletePublications() == 1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from password_reset_token where person_id = ?", Long.class, personId)).isEqualTo(1);

        incompleteEventPublications.resubmitIncompletePublications(this::belongsToThisPerson);

        verify(emailService, timeout(5000).times(2)).sendPasswordResetCode(any(Person.class), anyString());
        await().until(() -> allPublications() == 0);
    }

    @Test
    @Tag("D-178")
    @Tag("D-183")
    void resubmittingACompletedPublicationDoesNotDeliverTheEventAgain() {
        transactionTemplate.executeWithoutResult(status -> requestPasswordResetUseCase.requestReset(email));
        verify(emailService, timeout(5000)).sendPasswordResetCode(any(Person.class), anyString());
        await().until(() -> allPublications() == 0);

        incompleteEventPublications.resubmitIncompletePublications(this::belongsToThisPerson);

        await().during(Duration.ofSeconds(1)).until(() -> allPublications() == 0);
        verify(emailService, times(1)).sendPasswordResetCode(any(Person.class), anyString());
    }

    @Test
    @Tag("D-178")
    void anAsyncListenerKeepsTheRequestContextAndAddsTheEventCorrelationId() {
        MDC.put(CorrelationIds.REQUEST_ID_KEY, "request-7");
        MDC.put(CorrelationIds.USER_ID_KEY, "42");
        String correlationId = CorrelationIds.current();

        transactionTemplate.executeWithoutResult(status ->
                eventPublisher.publishEvent(new ProbeEvent(correlationId, Instant.now(), "payload")));

        await().until(() -> !recordingListener.snapshots().isEmpty());
        Map<String, String> snapshot = recordingListener.snapshots().peek();
        assertThat(snapshot).containsEntry(CorrelationIds.REQUEST_ID_KEY, "request-7")
                .containsEntry(CorrelationIds.USER_ID_KEY, "42")
                .containsEntry(CorrelationIds.CORRELATION_ID_KEY, "request-7");
    }

    @Test
    @Tag("D-178")
    void anEventPublishedWithoutARequestCarriesItsOwnCorrelationIdInTheListener() {
        transactionTemplate.executeWithoutResult(status ->
                eventPublisher.publishEvent(new ProbeEvent("job-correlation", Instant.now(), "payload")));

        await().until(() -> !recordingListener.snapshots().isEmpty());
        Map<String, String> snapshot = recordingListener.snapshots().peek();
        assertThat(snapshot).containsEntry(CorrelationIds.CORRELATION_ID_KEY, "job-correlation")
                .doesNotContainKey(CorrelationIds.REQUEST_ID_KEY);
    }

    @Test
    @Tag("D-178")
    void incompletePublicationsAreExposedAsGauges() {
        double incompleteBefore = meterRegistry.get("modulith.events.incomplete").gauge().value();
        jdbcTemplate.update("insert into event_publication (id, listener_id, event_type, serialized_event, publication_date) values (gen_random_uuid(), 'l', 't', '{}', now() - interval '10 minutes')");

        assertThat(meterRegistry.get("modulith.events.incomplete").gauge().value()).isEqualTo(incompleteBefore + 1.0);
        assertThat(meterRegistry.get("modulith.events.oldest.incomplete.seconds").gauge().value()).isGreaterThanOrEqualTo(600.0);
    }
}
