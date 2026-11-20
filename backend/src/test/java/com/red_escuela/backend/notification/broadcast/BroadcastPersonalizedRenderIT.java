package com.red_escuela.backend.notification.broadcast;

import com.red_escuela.backend.identity.api.NotificationPreferenceFinder;
import com.red_escuela.backend.identity.api.PersonContactFinder;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.domain.ChannelType;
import com.red_escuela.backend.notification.broadcast.domain.ConsentChannel;
import com.red_escuela.backend.notification.broadcast.domain.UnsubscribeClaims;
import com.red_escuela.backend.notification.broadcast.draft.domain.BroadcastStatus;
import com.red_escuela.backend.notification.broadcast.draft.entity.Broadcast;
import com.red_escuela.backend.notification.broadcast.draft.repository.BroadcastRepository;
import com.red_escuela.backend.notification.broadcast.dto.UnsubscribeLinkResponse;
import com.red_escuela.backend.notification.broadcast.lifecycle.specialist.BroadcastConfirmer;
import com.red_escuela.backend.notification.broadcast.specialist.UnsubscribeLinkFactory;
import com.red_escuela.backend.notification.broadcast.specialist.UnsubscribeTokenVerifier;
import com.red_escuela.backend.notification.broadcast.usecase.unsubscribe.DescribeUnsubscribeLinkUseCase;
import com.red_escuela.backend.notification.delivery.policy.EmailDeliveryContext;
import com.red_escuela.backend.notification.delivery.specialist.EmailDeliveryChannel;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import com.red_escuela.backend.shared.mail.TransactionalEmail;
import com.red_escuela.backend.shared.mail.TransactionalEmailGateway;
import com.red_escuela.backend.shared.resilience.CircuitBreakerFactory;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class BroadcastPersonalizedRenderIT extends AbstractBroadcastDispatchIT {

    private static final String SPEC = "{\"version\":1,\"quantifier\":\"ALL\",\"criteria\":[{\"key\":\"STAFF_ROLE\",\"value\":\"PRECEPTOR\"}]}";
    private static final LocalDate DAY = LocalDate.of(2041, 7, 8);
    private static final String LINK_PREFIX = "/baja?token=";

    @Autowired
    private BroadcastRepository broadcastRepository;
    @Autowired
    private BroadcastConfirmer confirmer;
    @Autowired
    private UnsubscribeLinkFactory linkFactory;
    @Autowired
    private UnsubscribeTokenVerifier tokenVerifier;
    @Autowired
    private DescribeUnsubscribeLinkUseCase describeUnsubscribeLink;
    @Autowired
    private PersonContactFinder personContactFinder;
    @Autowired
    private NotificationPreferenceFinder preferenceFinder;
    @Autowired
    private PolicySet<EmailDeliveryContext> emailDeliveryPolicySet;
    @Autowired
    private CircuitBreakerFactory circuitBreakerFactory;

    @BeforeEach
    void startWithNoPreceptors() {
        jdbc.update("delete from person_role where role_id = (select id from role where name = 'PRECEPTOR')");
        at(DAY, 9, 0);
    }

    @AfterEach
    void cleanBroadcasts() {
        jdbc.update("delete from broadcast_recipient");
        jdbc.update("delete from broadcast");
    }

    private Person preceptorNamed(String name) {
        Person person = persistPerson();
        jdbc.update("update person set name = ? where id = ?", name, person.getId());
        jdbc.update("insert into person_role (person_id, role_id) select ?, id from role where name = 'PRECEPTOR'", person.getId());
        grantConsent(person, BroadcastCategory.PROMOTIONAL);
        return person;
    }

    private Broadcast confirmedBroadcast(String subject, String body) {
        Person creator = persistPerson();
        Broadcast broadcast = broadcastRepository.saveAndFlush(Broadcast.builder().name("Aviso").category(BroadcastCategory.PROMOTIONAL)
                .channel(ChannelType.EMAIL).status(BroadcastStatus.DRAFT).segmentSpec(SPEC).subjectSnapshot(subject)
                .bodySnapshot(body).createdByPersonId(creator.getId()).createdAt(MutableClock.INSTANCE.instant()).build());
        confirmer.confirmBroadcast(broadcast.getId(), creator.getId());
        return broadcast;
    }

    private Long deliveryIdOf(Broadcast broadcast, Person person) {
        return jdbc.queryForObject("select delivery_id from broadcast_recipient where broadcast_id = ? and person_id = ?", Long.class,
                broadcast.getId(), person.getId());
    }

    private String bodyOf(Broadcast broadcast, Person person) {
        return jdbc.queryForObject("select body_text from notification_delivery where id = ?", String.class, deliveryIdOf(broadcast, person));
    }

    private String subjectOf(Broadcast broadcast, Person person) {
        return jdbc.queryForObject("select subject from notification_delivery where id = ?", String.class, deliveryIdOf(broadcast, person));
    }

    private static String tokenIn(String text) {
        int start = text.indexOf(LINK_PREFIX) + LINK_PREFIX.length();
        int end = text.indexOf(' ', start) < 0 ? text.length() : text.indexOf(' ', start);
        return URLDecoder.decode(text.substring(start, end).strip(), StandardCharsets.UTF_8);
    }

    private EmailDeliveryChannel capturingChannel(List<TransactionalEmail> sent) {
        TransactionalEmailGateway capturing = (TransactionalEmail email) -> {
            sent.add(email);
            return realGateway.send(email);
        };
        return new EmailDeliveryChannel(deliveryRepository, personContactFinder, preferenceFinder, emailDeliveryPolicySet, capturing,
                circuitBreakerFactory, linkFactory) {
            @Override
            public void dispatchDelivery(Long deliveryId) {
                transactionTemplate.executeWithoutResult(status -> super.dispatchDelivery(deliveryId));
            }
        };
    }

    @Test
    void eachRecipientGetsOwnName() {
        Person ana = preceptorNamed("Ana");
        Person beto = preceptorNamed("Beto");

        Broadcast broadcast = confirmedBroadcast("Aviso para {{nombre}}", "Hola {{nombre}}, gracias por tu trabajo.");

        assertThat(subjectOf(broadcast, ana)).isEqualTo("Aviso para Ana");
        assertThat(subjectOf(broadcast, beto)).isEqualTo("Aviso para Beto");
        assertThat(bodyOf(broadcast, ana)).startsWith("Hola Ana, gracias").doesNotContain("{{");
        assertThat(bodyOf(broadcast, beto)).startsWith("Hola Beto, gracias").doesNotContain("{{");
    }

    @Test
    void nameWithMarkupIsKeptAsPlainText() {
        Person person = preceptorNamed("<b>Ana</b> & Co");

        Broadcast broadcast = confirmedBroadcast("Aviso", "Hola {{nombre}}");

        assertThat(bodyOf(broadcast, person)).isEqualTo("Hola <b>Ana</b> & Co");
    }

    @Test
    void unsubscribeLinkResolvesToRecipient() {
        Person ana = preceptorNamed("Ana");
        Person beto = preceptorNamed("Beto");

        Broadcast broadcast = confirmedBroadcast("Aviso", "Hola {{nombre}}, para no recibir más: {{linkBaja}} fin");

        String anaToken = tokenIn(bodyOf(broadcast, ana));
        String betoToken = tokenIn(bodyOf(broadcast, beto));
        assertThat(anaToken).isNotEqualTo(betoToken);
        UnsubscribeClaims anaClaims = tokenVerifier.verifyToken(anaToken);
        assertThat(anaClaims.personId()).isEqualTo(ana.getId());
        assertThat(anaClaims.channel()).isEqualTo(ConsentChannel.EMAIL);
        assertThat(anaClaims.category()).isEqualTo(BroadcastCategory.PROMOTIONAL);
        assertThat(tokenVerifier.verifyToken(betoToken).personId()).isEqualTo(beto.getId());
        UnsubscribeLinkResponse description = describeUnsubscribeLink.describeUnsubscribeLink(anaToken);
        assertThat(description).isNotNull();
    }

    @Test
    void listUnsubscribeHeaderIsSent() {
        Person ana = preceptorNamed("Ana");
        Broadcast broadcast = confirmedBroadcast("Aviso", "Hola {{nombre}}");
        List<TransactionalEmail> sent = new CopyOnWriteArrayList<>();

        capturingChannel(sent).dispatchDelivery(deliveryIdOf(broadcast, ana));

        assertThat(sent).hasSize(1);
        String unsubscribeUrl = sent.get(0).unsubscribeUrl();
        assertThat(unsubscribeUrl).contains(LINK_PREFIX);
        UnsubscribeClaims claims = tokenVerifier.verifyToken(tokenIn(unsubscribeUrl));
        assertThat(claims.personId()).isEqualTo(ana.getId());
        assertThat(claims.category()).isEqualTo(BroadcastCategory.PROMOTIONAL);
    }

    @Test
    void transactionalEmailsCarryNoUnsubscribeHeader() {
        Person person = persistPerson();
        Long deliveryId = queueBroadcast(person, BroadcastCategory.INSTITUTIONAL, DAY);
        jdbc.update("update notification_delivery set category = 'ANNOUNCEMENT', kind = 'TEST_TRANSACTIONAL' where id = ?", deliveryId);
        List<TransactionalEmail> sent = new CopyOnWriteArrayList<>();

        capturingChannel(sent).dispatchDelivery(deliveryId);

        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).unsubscribeUrl()).isNull();
    }

    @Test
    void missingRequiredVariableSkipsRecipientWithReason() {
        Person ana = preceptorNamed("Ana");

        Broadcast broadcast = confirmedBroadcast("Aviso", "Hola {{nombre}}, tu carrera es {{carrera}}");

        Map<String, Object> recipient = jdbc.queryForMap("select status, exclusion_reason, delivery_id from broadcast_recipient where broadcast_id = ? and person_id = ?",
                broadcast.getId(), ana.getId());
        assertThat(recipient.get("status")).isEqualTo("SKIPPED");
        assertThat(recipient.get("exclusion_reason")).isEqualTo("MISSING_TEMPLATE_VARIABLE");
        assertThat(recipient.get("delivery_id")).isNull();
        assertThat(count("category = 'BROADCAST'")).isZero();
    }

    @Test
    void oneMissingVariableDoesNotBlockTheOtherRecipients() {
        Person ana = preceptorNamed("Ana");
        Person withoutName = preceptorNamed("Beto");
        jdbc.update("update person set name = ' ' where id = ?", withoutName.getId());

        Broadcast broadcast = confirmedBroadcast("Aviso", "Hola {{nombre}}");

        assertThat(bodyOf(broadcast, ana)).isEqualTo("Hola Ana");
        assertThat(jdbc.queryForObject("select exclusion_reason from broadcast_recipient where broadcast_id = ? and person_id = ?", String.class,
                broadcast.getId(), withoutName.getId())).isEqualTo("MISSING_TEMPLATE_VARIABLE");
    }

    @Test
    void optedOutAndUnconsentedRecipientsAreSkippedBeforeRenderingAndGetNoToken() {
        Person revoked = preceptorNamed("Revocada");
        revokeConsent(revoked, BroadcastCategory.PROMOTIONAL);
        jdbc.update("update person set name = ' ' where id = ?", revoked.getId());
        Person unconsented = persistPerson();
        jdbc.update("update person set name = ' ' where id = ?", unconsented.getId());
        jdbc.update("insert into person_role (person_id, role_id) select ?, id from role where name = 'PRECEPTOR'", unconsented.getId());
        Person neighbor = preceptorNamed("Vecina");

        Broadcast broadcast = confirmedBroadcast("Aviso", "Hola {{nombre}}, para no recibir más: {{linkBaja}} fin");

        assertThat(exclusionReasonOf(broadcast, revoked)).isEqualTo("OPTED_OUT");
        assertThat(exclusionReasonOf(broadcast, unconsented)).isEqualTo("NO_CONSENT");
        assertThat(deliveryIdOf(broadcast, revoked)).isNull();
        assertThat(deliveryIdOf(broadcast, unconsented)).isNull();
        assertThat(exclusionReasonOf(broadcast, neighbor)).isNull();
        assertThat(bodyOf(broadcast, neighbor)).startsWith("Hola Vecina");
        assertThat(count("category = 'BROADCAST'")).isEqualTo(1);
    }

    @Test
    void lineBreakInRecipientNameIsFlattenedInTheSubject() {
        Person person = preceptorNamed("Ana\r\nBcc: intruso@example.com");

        Broadcast broadcast = confirmedBroadcast("Aviso para {{nombre}}", "Hola {{nombre}}");

        assertThat(subjectOf(broadcast, person)).isEqualTo("Aviso para Ana Bcc: intruso@example.com");
    }

    private String exclusionReasonOf(Broadcast broadcast, Person person) {
        return jdbc.queryForObject("select exclusion_reason from broadcast_recipient where broadcast_id = ? and person_id = ?", String.class,
                broadcast.getId(), person.getId());
    }
}
