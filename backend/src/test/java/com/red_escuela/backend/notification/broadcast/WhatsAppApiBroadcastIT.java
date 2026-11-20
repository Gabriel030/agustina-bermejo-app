package com.red_escuela.backend.notification.broadcast;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.domain.ChannelAddress;
import com.red_escuela.backend.notification.broadcast.domain.ChannelType;
import com.red_escuela.backend.notification.broadcast.domain.ConsentChannel;
import com.red_escuela.backend.notification.broadcast.domain.ConsentSource;
import com.red_escuela.backend.notification.broadcast.domain.DenialReason;
import com.red_escuela.backend.notification.broadcast.domain.DispatchOutcome;
import com.red_escuela.backend.notification.broadcast.domain.OutboundMessage;
import com.red_escuela.backend.notification.broadcast.domain.RenderedContent;
import com.red_escuela.backend.notification.broadcast.draft.domain.BroadcastStatus;
import com.red_escuela.backend.notification.broadcast.draft.entity.Broadcast;
import com.red_escuela.backend.notification.broadcast.draft.repository.BroadcastRepository;
import com.red_escuela.backend.notification.broadcast.exception.ChannelNotEnabledException;
import com.red_escuela.backend.notification.broadcast.specialist.BroadcastChannelGate;
import com.red_escuela.backend.notification.broadcast.specialist.BroadcastConsentRecheck;
import com.red_escuela.backend.notification.broadcast.specialist.BroadcastDispatchSettings;
import com.red_escuela.backend.notification.broadcast.specialist.BroadcastWhatsAppDispatchJob;
import com.red_escuela.backend.notification.broadcast.specialist.ChannelRegistry;
import com.red_escuela.backend.notification.broadcast.specialist.ContactConsentLedger;
import com.red_escuela.backend.notification.broadcast.specialist.WhatsAppDeliverySender;
import com.red_escuela.backend.notification.broadcast.specialist.WhatsAppProviderSettings;
import com.red_escuela.backend.notification.broadcast.template.domain.WhatsAppTemplateStatus;
import com.red_escuela.backend.notification.broadcast.template.entity.MessageTemplate;
import com.red_escuela.backend.notification.broadcast.template.entity.WhatsAppTemplateMapping;
import com.red_escuela.backend.notification.broadcast.template.exception.WhatsappTemplateNotApprovedException;
import com.red_escuela.backend.notification.broadcast.template.repository.MessageTemplateRepository;
import com.red_escuela.backend.notification.broadcast.template.repository.WhatsAppTemplateMappingRepository;
import com.red_escuela.backend.notification.delivery.domain.ProviderReceipt;
import com.red_escuela.backend.notification.delivery.repository.NotificationDeliveryRepository;
import com.red_escuela.backend.notification.delivery.specialist.WhatsAppProvider;
import com.red_escuela.backend.notification.delivery.specialist.WhatsAppProviderRejectedException;
import com.red_escuela.backend.notification.delivery.specialist.WhatsAppProviderUnavailableException;
import com.red_escuela.backend.platform.infrastructure.InstituteSettingsSnapshotHolder;
import com.red_escuela.backend.shared.exception.CircuitOpenException;
import com.red_escuela.backend.testsupport.SharedMockBeans;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@SharedMockBeans
class WhatsAppApiBroadcastIT extends AbstractIntegrationTest {

    private static final String PHONE = "1112345678";
    private static final String NORMALIZED_PHONE = "+5491112345678";

    @Autowired
    private ChannelRegistry channelRegistry;
    @Autowired
    private ContactConsentLedger ledger;
    @Autowired
    private WhatsAppProvider provider;
    @Autowired
    private WhatsAppDeliverySender sender;
    @Autowired
    private NotificationDeliveryRepository deliveryRepository;
    @Autowired
    private BroadcastConsentRecheck consentRecheck;
    @Autowired
    private BroadcastChannelGate channelGate;
    @Autowired
    private MessageTemplateRepository messageTemplateRepository;
    @Autowired
    private WhatsAppTemplateMappingRepository mappingRepository;
    @Autowired
    private BroadcastRepository broadcastRepository;
    @Autowired
    private InstituteSettingsSnapshotHolder snapshotHolder;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private Clock clock;
    @Autowired
    private MeterRegistry meterRegistry;

    private Long creatorId;

    @BeforeEach
    void prepare() {
        creatorId = persistPerson().getId();
        jdbc.update("delete from notification_delivery where channel = 'WHATSAPP_API'");
        switchChannel(true);
    }

    @AfterEach
    void restore() {
        jdbc.update("delete from notification_delivery where channel = 'WHATSAPP_API'");
        switchChannel(false);
    }

    @Test
    void nothingIsSentWhileSwitchIsOff() {
        Person queuedPerson = persistPerson();
        Long queuedId = queueOne(queuedPerson);
        Person person = persistPerson();
        long broadcastId = persistBroadcast(WhatsAppTemplateStatus.APPROVED, BroadcastCategory.INSTITUTIONAL);
        recordConsent(person, BroadcastCategory.INSTITUTIONAL, true);
        switchChannel(false);

        assertThatThrownBy(() -> channelRegistry.channel(ChannelType.WHATSAPP_API).dispatch(message(person, broadcastId, BroadcastCategory.INSTITUTIONAL)))
                .isInstanceOf(ChannelNotEnabledException.class);
        assertThat(jobWith(300).dispatchPendingBroadcastWhatsApp()).isZero();

        assertThat(deliveriesOf(person)).isZero();
        assertThat(row(queuedId)).containsEntry("status", "QUEUED").containsEntry("attempts", 0);
        verifyNoInteractions(provider);
    }

    @Test
    void recipientWithoutExplicitConsentIsRejectedEvenForInstitutionalMessages() {
        Person person = persistPerson();
        long broadcastId = persistBroadcast(WhatsAppTemplateStatus.APPROVED, BroadcastCategory.INSTITUTIONAL);

        DispatchOutcome outcome = channelRegistry.channel(ChannelType.WHATSAPP_API).dispatch(message(person, broadcastId, BroadcastCategory.INSTITUTIONAL));

        assertThat(outcome).isEqualTo(new DispatchOutcome.Rejected(DenialReason.NO_CONSENT));
        assertThat(deliveriesOf(person)).isZero();
    }

    @Test
    void revokedConsentIsRejectedAsOptedOut() {
        Person person = persistPerson();
        long broadcastId = persistBroadcast(WhatsAppTemplateStatus.APPROVED, BroadcastCategory.INSTITUTIONAL);
        recordConsent(person, BroadcastCategory.INSTITUTIONAL, false);

        DispatchOutcome outcome = channelRegistry.channel(ChannelType.WHATSAPP_API).dispatch(message(person, broadcastId, BroadcastCategory.INSTITUTIONAL));

        assertThat(outcome).isEqualTo(new DispatchOutcome.Rejected(DenialReason.OPTED_OUT));
    }

    @Test
    void approvedTemplateIsSentAndRecordsTheMessageCategory() {
        Person utilityPerson = persistPerson();
        Person marketingPerson = persistPerson();
        long utilityBroadcast = persistBroadcast(WhatsAppTemplateStatus.APPROVED, BroadcastCategory.INSTITUTIONAL);
        long marketingBroadcast = persistBroadcast(WhatsAppTemplateStatus.APPROVED, BroadcastCategory.PROMOTIONAL);
        recordConsent(utilityPerson, BroadcastCategory.INSTITUTIONAL, true);
        recordConsent(marketingPerson, BroadcastCategory.PROMOTIONAL, true);
        when(provider.sendTemplate(anyString(), anyString(), anyString(), anyList())).thenReturn(new ProviderReceipt("wamid.OK"));

        Long utilityId = queuedId(channelRegistry.channel(ChannelType.WHATSAPP_API)
                .dispatch(message(utilityPerson, utilityBroadcast, BroadcastCategory.INSTITUTIONAL)));
        Long marketingId = queuedId(channelRegistry.channel(ChannelType.WHATSAPP_API)
                .dispatch(message(marketingPerson, marketingBroadcast, BroadcastCategory.PROMOTIONAL)));
        assertThat(jobWith(300).dispatchPendingBroadcastWhatsApp()).isEqualTo(2);

        verify(provider, times(2)).sendTemplate(NORMALIZED_PHONE, "aviso_general", "es_AR", List.of("Nombre"));
        assertThat(row(utilityId)).containsEntry("status", "SENT").containsEntry("provider_message_id", "wamid.OK")
                .containsEntry("pricing_category", "UTILITY").containsEntry("channel", "WHATSAPP_API");
        assertThat(row(marketingId)).containsEntry("status", "SENT").containsEntry("pricing_category", "MARKETING");
    }

    @Test
    void templateThatIsNotApprovedIsNeverQueued() {
        Person person = persistPerson();
        long broadcastId = persistBroadcast(WhatsAppTemplateStatus.PENDING, BroadcastCategory.INSTITUTIONAL);
        recordConsent(person, BroadcastCategory.INSTITUTIONAL, true);

        assertThatThrownBy(() -> channelRegistry.channel(ChannelType.WHATSAPP_API).dispatch(message(person, broadcastId, BroadcastCategory.INSTITUTIONAL)))
                .isInstanceOf(WhatsappTemplateNotApprovedException.class);
        assertThat(deliveriesOf(person)).isZero();
    }

    @Test
    void policyRejectionFailsWithTheReasonAndIsNotRetried() {
        Person person = persistPerson();
        Long deliveryId = queueOne(person);
        when(provider.sendTemplate(anyString(), anyString(), anyString(), anyList())).thenThrow(new WhatsAppProviderRejectedException(400, "132001"));
        BroadcastWhatsAppDispatchJob job = jobWith(300);

        job.dispatchPendingBroadcastWhatsApp();
        job.dispatchPendingBroadcastWhatsApp();

        verify(provider, times(1)).sendTemplate(anyString(), anyString(), anyString(), anyList());
        assertThat(row(deliveryId)).containsEntry("status", "FAILED").containsEntry("status_reason", "PROVIDER_REJECTED")
                .containsEntry("provider_error_code", "132001").containsEntry("attempts", 1);
    }

    @Test
    void transientErrorsAreRetriedUpToTheMaximumAttempts() {
        Person person = persistPerson();
        Long deliveryId = queueOne(person);
        when(provider.sendTemplate(anyString(), anyString(), anyString(), anyList())).thenThrow(new WhatsAppProviderUnavailableException("down", null));
        BroadcastWhatsAppDispatchJob job = jobWith(300);

        job.dispatchPendingBroadcastWhatsApp();
        assertThat(row(deliveryId)).containsEntry("status", "QUEUED").containsEntry("attempts", 1);
        job.dispatchPendingBroadcastWhatsApp();
        job.dispatchPendingBroadcastWhatsApp();
        job.dispatchPendingBroadcastWhatsApp();

        verify(provider, times(3)).sendTemplate(anyString(), anyString(), anyString(), anyList());
        assertThat(row(deliveryId)).containsEntry("status", "FAILED").containsEntry("status_reason", "PROVIDER_ERROR").containsEntry("attempts", 3);
    }

    @Test
    void transientErrorFollowedBySuccessEndsSent() {
        Person person = persistPerson();
        Long deliveryId = queueOne(person);
        when(provider.sendTemplate(anyString(), anyString(), anyString(), anyList()))
                .thenThrow(new WhatsAppProviderUnavailableException("down", null))
                .thenReturn(new ProviderReceipt("wamid.RETRY"));
        BroadcastWhatsAppDispatchJob job = jobWith(300);

        job.dispatchPendingBroadcastWhatsApp();
        job.dispatchPendingBroadcastWhatsApp();

        assertThat(row(deliveryId)).containsEntry("status", "SENT").containsEntry("provider_message_id", "wamid.RETRY");
    }

    @Test
    void openCircuitDoesNotConsumeAttempts() {
        Person person = persistPerson();
        Long deliveryId = queueOne(person);
        when(provider.sendTemplate(anyString(), anyString(), anyString(), anyList()))
                .thenThrow(new CircuitOpenException("whatsapp-meta", Duration.ofSeconds(30)));

        jobWith(300).dispatchPendingBroadcastWhatsApp();

        assertThat(row(deliveryId)).containsEntry("status", "QUEUED").containsEntry("attempts", 0);
    }

    @Test
    void dailyLimitStopsTheDispatch() {
        Person first = persistPerson();
        Person second = persistPerson();
        Long firstId = queueOne(first);
        Long secondId = queueOne(second);
        when(provider.sendTemplate(anyString(), anyString(), anyString(), anyList())).thenReturn(new ProviderReceipt("wamid.CAP"));
        BroadcastWhatsAppDispatchJob job = jobWith(1);

        job.dispatchPendingBroadcastWhatsApp();
        job.dispatchPendingBroadcastWhatsApp();

        verify(provider, times(1)).sendTemplate(anyString(), anyString(), anyString(), anyList());
        assertThat(List.of(row(firstId).get("status"), row(secondId).get("status"))).containsExactlyInAnyOrder("SENT", "QUEUED");
    }

    @Test
    void consentWithdrawnAfterQueueingSuppressesTheSend() {
        Person person = persistPerson();
        long broadcastId = persistBroadcast(WhatsAppTemplateStatus.APPROVED, BroadcastCategory.INSTITUTIONAL);
        recordConsent(person, BroadcastCategory.INSTITUTIONAL, true);
        Long deliveryId = queuedId(channelRegistry.channel(ChannelType.WHATSAPP_API).dispatch(message(person, broadcastId, BroadcastCategory.INSTITUTIONAL)));
        recordConsent(person, BroadcastCategory.INSTITUTIONAL, false);

        jobWith(300).dispatchPendingBroadcastWhatsApp();

        verify(provider, never()).sendTemplate(any(), any(), any(), any());
        assertThat(row(deliveryId)).containsEntry("status", "SUPPRESSED").containsEntry("status_reason", "OPTED_OUT");
    }

    @Test
    void personWithoutPhoneIsSuppressed() {
        Person person = persistPerson(null);
        Long deliveryId = queueOne(person);

        jobWith(300).dispatchPendingBroadcastWhatsApp();

        verify(provider, never()).sendTemplate(any(), any(), any(), any());
        assertThat(row(deliveryId)).containsEntry("status", "SUPPRESSED").containsEntry("status_reason", "NO_WHATSAPP_NUMBER");
    }

    private BroadcastWhatsAppDispatchJob jobWith(int dailyLimit) {
        return new BroadcastWhatsAppDispatchJob(deliveryRepository, sender, consentRecheck,
                new BroadcastDispatchSettings(300, 100, 100, "00:00", "23:59"), new WhatsAppProviderSettings(dailyLimit), channelGate, clock, meterRegistry);
    }

    private Long queueOne(Person person) {
        long broadcastId = persistBroadcast(WhatsAppTemplateStatus.APPROVED, BroadcastCategory.INSTITUTIONAL);
        recordConsent(person, BroadcastCategory.INSTITUTIONAL, true);
        return queuedId(channelRegistry.channel(ChannelType.WHATSAPP_API).dispatch(message(person, broadcastId, BroadcastCategory.INSTITUTIONAL)));
    }

    private Long queuedId(DispatchOutcome outcome) {
        assertThat(outcome).isInstanceOf(DispatchOutcome.Queued.class);
        return ((DispatchOutcome.Queued) outcome).deliveryId();
    }

    private Map<String, Object> row(Long deliveryId) {
        return jdbc.queryForMap("select channel, status, status_reason, attempts, provider_message_id, provider_error_code, pricing_category from notification_delivery where id = ?", deliveryId);
    }

    private int deliveriesOf(Person person) {
        return jdbc.queryForObject("select count(*) from notification_delivery where recipient_person_id = ?", Integer.class, person.getId());
    }

    private OutboundMessage message(Person person, long broadcastId, BroadcastCategory category) {
        return new OutboundMessage(broadcastId, person.getId(), ChannelAddress.ofPhone(NORMALIZED_PHONE),
                new RenderedContent(null, "Hola Nombre"), category);
    }

    private void switchChannel(boolean enabled) {
        jdbc.update("update institute_setting set enabled = ? where code = 'WHATSAPP_API'", enabled);
        snapshotHolder.refresh();
    }

    private void recordConsent(Person person, BroadcastCategory category, boolean granted) {
        transactionTemplate.executeWithoutResult(status ->
                ledger.recordConsent(person.getId(), ConsentChannel.WHATSAPP, category, granted, ConsentSource.PROFILE, null));
    }

    private Person persistPerson() {
        return persistPerson(PHONE);
    }

    private Person persistPerson(String phone) {
        String username = "wa-" + System.nanoTime();
        return transactionTemplate.execute(status -> {
            com.red_escuela.backend.shared.domain.Address address = new com.red_escuela.backend.shared.domain.Address();
            entityManager.persist(address);
            Person person = new Person();
            person.setName("Nombre");
            person.setLastname(username);
            person.setUsername(username);
            person.setEmail(username + "@example.com");
            person.setEmailVerified(true);
            person.setPhoneNumber(phone);
            person.setAddress(address);
            person.setStatus(com.red_escuela.backend.shared.domain.StatusType.ACTIVE);
            entityManager.persist(person);
            return person;
        });
    }

    private long persistBroadcast(WhatsAppTemplateStatus mappingStatus, BroadcastCategory category) {
        return transactionTemplate.execute(status -> {
            Instant now = clock.instant();
            MessageTemplate template = messageTemplateRepository.save(MessageTemplate.builder().name("tpl-" + System.nanoTime())
                    .category(category).channel(ChannelType.WHATSAPP_API).bodyText("Hola {{nombre}}").active(true)
                    .createdByPersonId(creatorId).createdAt(now).updatedAt(now).build());
            mappingRepository.save(WhatsAppTemplateMapping.builder().messageTemplateId(template.getId()).providerTemplateName("aviso_general")
                    .languageCode("es_AR").status(mappingStatus).variableOrder(List.of("nombre")).updatedBy(1L).updatedAt(now).build());
            return broadcastRepository.save(Broadcast.builder().name("Aviso").category(category).channel(ChannelType.WHATSAPP_API)
                    .status(BroadcastStatus.DRAFT).segmentSpec("{\"version\":1}").bodySnapshot("Hola {{nombre}}").templateId(template.getId())
                    .createdByPersonId(creatorId).createdAt(now).build()).getId();
        });
    }
}
