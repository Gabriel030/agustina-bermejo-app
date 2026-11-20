package com.red_escuela.backend.notification.broadcast;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.domain.ChannelAddress;
import com.red_escuela.backend.notification.broadcast.domain.ChannelType;
import com.red_escuela.backend.notification.broadcast.domain.DeliveryMode;
import com.red_escuela.backend.notification.broadcast.domain.DenialReason;
import com.red_escuela.backend.notification.broadcast.domain.DispatchOutcome;
import com.red_escuela.backend.notification.broadcast.domain.OutboundMessage;
import com.red_escuela.backend.notification.broadcast.domain.RenderedContent;
import com.red_escuela.backend.notification.broadcast.specialist.ChannelRegistry;
import com.red_escuela.backend.notification.broadcast.specialist.MessageChannel;
import com.red_escuela.backend.platform.infrastructure.InstituteSettingsSnapshotHolder;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.testsupport.SharedMockBeans;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@SharedMockBeans
class MessageChannelContractTest extends AbstractIntegrationTest {

    @Autowired
    private List<MessageChannel> channels;
    @Autowired
    private ChannelRegistry channelRegistry;
    @Autowired
    private InstituteSettingsSnapshotHolder snapshotHolder;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void turnChannelOn() {
        jdbc.update("update institute_setting set enabled = true where code = 'WHATSAPP_API'");
        snapshotHolder.refresh();
    }

    @AfterEach
    void turnChannelOff() {
        jdbc.update("update institute_setting set enabled = false where code = 'WHATSAPP_API'");
        snapshotHolder.refresh();
    }

    @Test
    void everyAutomaticChannelIsRegisteredUnderItsOwnType() {
        assertThat(channels).extracting(MessageChannel::type).doesNotHaveDuplicates()
                .contains(ChannelType.EMAIL, ChannelType.WHATSAPP_API);
        channels.forEach(channel -> assertThat(channelRegistry.channel(channel.type())).isSameAs(channel));
    }

    @Test
    void everyChannelDeclaresUsableCapabilitiesAndMode() {
        channels.forEach(channel -> {
            assertThat(channel.deliveryMode()).isEqualTo(DeliveryMode.AUTOMATIC);
            assertThat(channel.capabilities().maxBodyLength()).isPositive();
        });
        MessageChannel whatsApp = channelRegistry.channel(ChannelType.WHATSAPP_API);
        assertThat(whatsApp.capabilities().requiresOptIn()).isTrue();
        assertThat(whatsApp.capabilities().supportsTemplates()).isTrue();
    }

    @Test
    void everyChannelRejectsPromotionalMessagesWithoutConsentAndEnqueuesNothing() {
        Person person = persistPerson();
        channels.forEach(channel -> {
            ChannelAddress address = channel.resolveAddress(person.getId()).orElseThrow();
            DispatchOutcome outcome = channel.dispatch(new OutboundMessage(System.nanoTime(), person.getId(), address,
                    new RenderedContent("Asunto", "Texto"), BroadcastCategory.PROMOTIONAL));
            assertThat(outcome).isEqualTo(new DispatchOutcome.Rejected(DenialReason.NO_CONSENT));
        });
        assertThat(jdbc.queryForObject("select count(*) from notification_delivery where recipient_person_id = ?", Integer.class, person.getId())).isZero();
    }

    @Test
    void whatsAppResolvesAnE164PhoneAndEmailResolvesAnEmail() {
        Person person = persistPerson();

        assertThat(channelRegistry.channel(ChannelType.WHATSAPP_API).resolveAddress(person.getId()))
                .contains(ChannelAddress.ofPhone("+5491112345678"));
        assertThat(channelRegistry.channel(ChannelType.EMAIL).resolveAddress(person.getId()).orElseThrow().kind())
                .isEqualTo(ChannelAddress.Kind.EMAIL);
    }

    private Person persistPerson() {
        String username = "ct-" + System.nanoTime();
        return transactionTemplate.execute(status -> {
            Address address = new Address();
            entityManager.persist(address);
            Person person = new Person();
            person.setName("Nombre");
            person.setLastname(username);
            person.setUsername(username);
            person.setEmail(username + "@example.com");
            person.setEmailVerified(true);
            person.setPhoneNumber("1112345678");
            person.setAddress(address);
            person.setStatus(StatusType.ACTIVE);
            entityManager.persist(person);
            return person;
        });
    }
}
