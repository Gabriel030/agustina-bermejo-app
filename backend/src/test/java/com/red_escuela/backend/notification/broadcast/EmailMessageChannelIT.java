package com.red_escuela.backend.notification.broadcast;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.domain.ChannelAddress;
import com.red_escuela.backend.notification.broadcast.domain.ChannelType;
import com.red_escuela.backend.notification.broadcast.domain.ConsentChannel;
import com.red_escuela.backend.notification.broadcast.domain.ConsentSource;
import com.red_escuela.backend.notification.broadcast.domain.DenialReason;
import com.red_escuela.backend.notification.broadcast.domain.DispatchOutcome;
import com.red_escuela.backend.notification.broadcast.domain.OutboundMessage;
import com.red_escuela.backend.notification.broadcast.domain.RenderedContent;
import com.red_escuela.backend.notification.broadcast.specialist.ChannelRegistry;
import com.red_escuela.backend.notification.broadcast.specialist.ContactConsentLedger;
import com.red_escuela.backend.notification.delivery.domain.DeliveryCategory;
import com.red_escuela.backend.notification.delivery.domain.DeliveryContent;
import com.red_escuela.backend.notification.delivery.domain.DeliveryEnvelope;
import com.red_escuela.backend.notification.delivery.specialist.DeliveryRequest;
import com.red_escuela.backend.notification.delivery.specialist.NotificationDeliveryEnqueuer;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class EmailMessageChannelIT extends AbstractIntegrationTest {

    @Autowired
    private ChannelRegistry channelRegistry;
    @Autowired
    private ContactConsentLedger ledger;
    @Autowired
    private NotificationDeliveryEnqueuer deliveryEnqueuer;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;

    private Person persistPerson() {
        String dni = "8" + System.nanoTime();
        return transactionTemplate.execute(status -> {
            Address address = new Address();
            entityManager.persist(address);
            Person person = new Person();
            person.setName("Persona");
            person.setLastname(dni);
            person.setDni(dni);
            person.setUsername(dni);
            person.setEmail(dni + "@example.com");
            person.setEmailVerified(true);
            person.setAddress(address);
            person.setStatus(StatusType.ACTIVE);
            entityManager.persist(person);
            return person;
        });
    }

    private void recordConsent(Person person, BroadcastCategory category, boolean granted) {
        transactionTemplate.executeWithoutResult(status ->
                ledger.recordConsent(person.getId(), ConsentChannel.EMAIL, category, granted, ConsentSource.PROFILE, null));
    }

    private OutboundMessage message(Person person, long broadcastId, BroadcastCategory category) {
        return new OutboundMessage(broadcastId, person.getId(), ChannelAddress.ofEmail(person.getEmail().value()),
                new RenderedContent("Novedades", "Texto de la comunicación"), category);
    }

    private int deliveriesOf(Person person) {
        return jdbc.queryForObject("select count(*) from notification_delivery where recipient_person_id = ?", Integer.class, person.getId());
    }

    @Test
    void queuesBroadcastDeliveryWhenConsentAllows() {
        Person person = persistPerson();
        long broadcastId = System.nanoTime();

        DispatchOutcome outcome = channelRegistry.channel(ChannelType.EMAIL).dispatch(message(person, broadcastId, BroadcastCategory.INSTITUTIONAL));

        assertThat(outcome).isInstanceOf(DispatchOutcome.Queued.class);
        Long deliveryId = ((DispatchOutcome.Queued) outcome).deliveryId();
        Map<String, Object> row = jdbc.queryForMap("select category, channel, status, dedup_key, subject from notification_delivery where id = ?", deliveryId);
        assertThat(row).containsEntry("category", "BROADCAST").containsEntry("channel", "EMAIL").containsEntry("status", "QUEUED")
                .containsEntry("dedup_key", "BROADCAST_" + broadcastId + "_" + person.getId()).containsEntry("subject", "Novedades");
    }

    @Test
    void queuesPromotionalOnlyAfterGrant() {
        Person person = persistPerson();
        recordConsent(person, BroadcastCategory.PROMOTIONAL, true);

        assertThat(channelRegistry.channel(ChannelType.EMAIL).dispatch(message(person, System.nanoTime(), BroadcastCategory.PROMOTIONAL)))
                .isInstanceOf(DispatchOutcome.Queued.class);
    }

    @Test
    void rejectsWithoutEnqueuingWhenPromotionalHasNoConsent() {
        Person person = persistPerson();

        DispatchOutcome outcome = channelRegistry.channel(ChannelType.EMAIL).dispatch(message(person, System.nanoTime(), BroadcastCategory.PROMOTIONAL));

        assertThat(outcome).isEqualTo(new DispatchOutcome.Rejected(DenialReason.NO_CONSENT));
        assertThat(deliveriesOf(person)).isZero();
    }

    @Test
    void rejectsInstitutionalWhenOptedOut() {
        Person person = persistPerson();
        recordConsent(person, BroadcastCategory.INSTITUTIONAL, false);

        DispatchOutcome outcome = channelRegistry.channel(ChannelType.EMAIL).dispatch(message(person, System.nanoTime(), BroadcastCategory.INSTITUTIONAL));

        assertThat(outcome).isEqualTo(new DispatchOutcome.Rejected(DenialReason.OPTED_OUT));
        assertThat(deliveriesOf(person)).isZero();
    }

    @Test
    void mandatoryEmailsDoNotEvaluateBroadcastConsent() {
        Person person = persistPerson();
        recordConsent(person, BroadcastCategory.INSTITUTIONAL, false);
        String dedupKey = "ANNOUNCEMENT_" + System.nanoTime();
        DeliveryEnvelope envelope = new DeliveryEnvelope(dedupKey, DeliveryCategory.ANNOUNCEMENT, "ANNOUNCEMENT_PUBLISHED", 1,
                person.getId(), "Announcement", 1L, LocalDate.of(2026, 3, 9));

        deliveryEnqueuer.enqueue(new DeliveryRequest(envelope, null, null, DeliveryContent.forEmail("Aviso", "Texto", null), false));

        assertThat(jdbc.queryForMap("select status, status_reason from notification_delivery where dedup_key = ?", dedupKey))
                .containsEntry("status", "QUEUED").containsEntry("status_reason", null);
    }

    @Test
    void resolvesTheVerifiedEmailAsAddress() {
        Person person = persistPerson();

        assertThat(channelRegistry.channel(ChannelType.EMAIL).resolveAddress(person.getId()))
                .contains(ChannelAddress.ofEmail(person.getEmail().value()));
        assertThat(channelRegistry.channel(ChannelType.EMAIL).resolveAddress(-1L)).isEmpty();
    }
}
