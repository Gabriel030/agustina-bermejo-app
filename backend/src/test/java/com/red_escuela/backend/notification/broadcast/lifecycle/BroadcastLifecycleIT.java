package com.red_escuela.backend.notification.broadcast.lifecycle;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.notification.broadcast.draft.AudienceFreezeFixtures;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.notification.broadcast.draft.domain.BroadcastStatus;
import com.red_escuela.backend.notification.broadcast.draft.entity.Broadcast;
import com.red_escuela.backend.notification.broadcast.draft.repository.BroadcastRepository;
import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.domain.ChannelType;
import com.red_escuela.backend.notification.broadcast.freezer.domain.BroadcastRecipientStatus;
import com.red_escuela.backend.notification.broadcast.freezer.repository.BroadcastRecipientRepository;
import jakarta.persistence.EntityManager;
import com.red_escuela.backend.config.TestDatabaseIsolation;
import com.red_escuela.backend.notification.broadcast.audience.domain.ExclusionReason;
import com.red_escuela.backend.notification.broadcast.domain.ConsentChannel;
import com.red_escuela.backend.notification.broadcast.domain.ConsentSource;
import com.red_escuela.backend.notification.broadcast.specialist.ContactConsentLedger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class BroadcastLifecycleIT extends AbstractIntegrationTest {

    private static final String SPEC = "{\"version\":1,\"quantifier\":\"ALL\",\"criteria\":[{\"key\":\"STAFF_ROLE\",\"value\":\"PRECEPTOR\"}]}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private BroadcastRepository broadcastRepository;

    @Autowired
    private BroadcastRecipientRepository recipientRepository;

    @Autowired
    private ContactConsentLedger contactConsentLedger;

    @BeforeEach
    void restoreCleanDatabase() {
        TestDatabaseIsolation.restoreNow();
    }

    private String bearer(Person person) {
        return "Bearer " + jwtService.generateAccessToken(person);
    }

    private Person person(String username, boolean emailVerified, String role) {
        String unique = username + System.nanoTime();
        return transactionTemplate.execute(status -> AudienceFreezeFixtures.person(entityManager, roleRepository, unique, emailVerified, role));
    }

    private Person director(String username) {
        return person(username, true, "DIRECTOR");
    }

    private BroadcastRecipientStatus recipientStatus(Broadcast broadcast, Person person) {
        return recipientRepository.findByBroadcastIdAndStatusIn(broadcast.getId(), List.of(BroadcastRecipientStatus.values())).stream()
                .filter(row -> row.getPersonId().equals(person.getId())).findFirst().orElseThrow().getStatus();
    }

    private long deliveriesOf(Person person, String status) {
        return count("select count(*) from notification_delivery where recipient_person_id = ? and related_entity_type = 'BROADCAST' and status = ?", person.getId(), status);
    }

    private Broadcast draft(Person creator) {
        return broadcastRepository.saveAndFlush(Broadcast.builder().name("Aviso").category(BroadcastCategory.INSTITUTIONAL)
                .channel(ChannelType.EMAIL).status(BroadcastStatus.DRAFT).segmentSpec(SPEC).subjectSnapshot("Asunto")
                .bodySnapshot("Cuerpo").createdByPersonId(creator.getId()).createdAt(Instant.now()).build());
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private void confirm(Broadcast broadcast, Person actor) throws Exception {
        mockMvc.perform(post("/broadcasts/" + broadcast.getId() + "/confirm").header("Authorization", bearer(actor))).andExpect(status().isOk());
    }

    @Test
    void confirmFreezesRecipientsQueuesDeliveriesAndAudits() throws Exception {
        Person director = director("lc-confirm-director");
        Person eligible = person("lc-confirm-a", true, "PRECEPTOR");
        Person unverified = person("lc-confirm-b", false, "PRECEPTOR");
        Broadcast broadcast = draft(director);

        mockMvc.perform(post("/broadcasts/" + broadcast.getId() + "/confirm").header("Authorization", bearer(director)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        assertThat(recipientStatus(broadcast, eligible)).isEqualTo(BroadcastRecipientStatus.QUEUED);
        assertThat(recipientStatus(broadcast, unverified)).isEqualTo(BroadcastRecipientStatus.EXCLUDED);
        assertThat(deliveriesOf(eligible, "QUEUED")).isEqualTo(1);
        assertThat(deliveriesOf(unverified, "QUEUED")).isZero();
        assertThat(count("select count(*) from audit_log where action = 'BROADCAST_CONFIRMED' and entity_type = 'BROADCAST' and entity_id = ?", broadcast.getId())).isEqualTo(1);
    }

    @Test
    void confirmIsIdempotent() throws Exception {
        Person director = director("lc-idem-director");
        Person eligible = person("lc-idem-a", true, "PRECEPTOR");
        Broadcast broadcast = draft(director);

        confirm(broadcast, director);
        long recipientsAfterFirst = recipientRepository.countByBroadcastId(broadcast.getId());
        confirm(broadcast, director);

        assertThat(deliveriesOf(eligible, "QUEUED")).isEqualTo(1);
        assertThat(recipientRepository.countByBroadcastId(broadcast.getId())).isEqualTo(recipientsAfterFirst);
        assertThat(count("select count(*) from audit_log where action = 'BROADCAST_CONFIRMED' and entity_id = ?", broadcast.getId())).isEqualTo(1);
    }

    @Test
    void confirmWithoutEligibleRecipientsIsRejectedAndLeavesTheDraft() throws Exception {
        Person director = director("lc-empty-director");
        person("lc-empty-a", false, "ADMINISTRATIVO");
        Broadcast broadcast = broadcastRepository.saveAndFlush(Broadcast.builder().name("Vacio").category(BroadcastCategory.INSTITUTIONAL)
                .channel(ChannelType.EMAIL).status(BroadcastStatus.DRAFT)
                .segmentSpec(SPEC.replace("PRECEPTOR", "ADMINISTRATIVO")).subjectSnapshot("Asunto").bodySnapshot("Cuerpo")
                .createdByPersonId(director.getId()).createdAt(Instant.now()).build());

        mockMvc.perform(post("/broadcasts/" + broadcast.getId() + "/confirm").header("Authorization", bearer(director)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMPTY_AUDIENCE"));

        assertThat(broadcastRepository.findById(broadcast.getId()).orElseThrow().getStatus()).isEqualTo(BroadcastStatus.DRAFT);
        assertThat(recipientRepository.countByBroadcastId(broadcast.getId())).isZero();
    }

    @Test
    void cancelStopsPendingDeliveries() throws Exception {
        Person director = director("lc-cancel-director");
        Person eligible = person("lc-cancel-a", true, "PRECEPTOR");
        Broadcast broadcast = draft(director);
        confirm(broadcast, director);
        assertThat(deliveriesOf(eligible, "QUEUED")).isEqualTo(1);

        mockMvc.perform(post("/broadcasts/" + broadcast.getId() + "/cancel").header("Authorization", bearer(director))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Error en el texto\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(deliveriesOf(eligible, "QUEUED")).isZero();
        assertThat(deliveriesOf(eligible, "DISCARDED")).isEqualTo(1);
        assertThat(recipientStatus(broadcast, eligible)).isEqualTo(BroadcastRecipientStatus.SKIPPED);
        assertThat(count("select count(*) from broadcast_recipient where broadcast_id = ? and status in ('QUEUED', 'PENDING')", broadcast.getId())).isZero();
        assertThat(count("select count(*) from audit_log where action = 'BROADCAST_CANCELLED' and entity_id = ? and reason = 'Error en el texto'", broadcast.getId())).isEqualTo(1);
    }

    @Test
    void cancelRequiresAReason() throws Exception {
        Person director = director("lc-reason-director");
        Broadcast broadcast = draft(director);

        mockMvc.perform(post("/broadcasts/" + broadcast.getId() + "/cancel").header("Authorization", bearer(director))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cancelledBroadcastRejectsAnotherCancelAndAConfirm() throws Exception {
        Person director = director("lc-twice-director");
        Broadcast broadcast = draft(director);
        mockMvc.perform(post("/broadcasts/" + broadcast.getId() + "/cancel").header("Authorization", bearer(director))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Motivo\"}")).andExpect(status().isOk());

        mockMvc.perform(post("/broadcasts/" + broadcast.getId() + "/cancel").header("Authorization", bearer(director))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Motivo\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ILLEGAL_BROADCAST_STATUS_TRANSITION"));
        mockMvc.perform(post("/broadcasts/" + broadcast.getId() + "/confirm").header("Authorization", bearer(director)))
                .andExpect(status().isConflict());
    }

    private void cancel(Broadcast broadcast, Person actor, int expectedStatus, String expectedCode) throws Exception {
        var result = mockMvc.perform(post("/broadcasts/" + broadcast.getId() + "/cancel").header("Authorization", bearer(actor))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Motivo\"}")).andExpect(status().is(expectedStatus));
        if (expectedCode != null) {
            result.andExpect(jsonPath("$.code").value(expectedCode));
        }
    }

    private void forceStatus(Broadcast broadcast, String status) {
        jdbc.update("update broadcast set status = ? where id = ?", status, broadcast.getId());
    }

    @Test
    void concurrentConfirmationsQueueEachRecipientOnceAndAuditOnce() throws Exception {
        Person director = director("lc-conc-director");
        Person first = person("lc-conc-a", true, "PRECEPTOR");
        Person second = person("lc-conc-b", true, "PRECEPTOR");
        Broadcast broadcast = draft(director);
        String authorization = bearer(director);
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = List.of(0, 1).stream().map(index -> executor.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return mockMvc.perform(post("/broadcasts/" + broadcast.getId() + "/confirm").header("Authorization", authorization))
                        .andReturn().getResponse().getStatus();
            })).toList();
            for (Future<Integer> result : results) {
                assertThat(result.get(30, TimeUnit.SECONDS)).isEqualTo(200);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(deliveriesOf(first, "QUEUED")).isEqualTo(1);
        assertThat(deliveriesOf(second, "QUEUED")).isEqualTo(1);
        assertThat(recipientRepository.countByBroadcastId(broadcast.getId())).isEqualTo(2);
        assertThat(count("select count(*) from audit_log where action = 'BROADCAST_CONFIRMED' and entity_id = ?", broadcast.getId())).isEqualTo(1);
        assertThat(broadcastRepository.findById(broadcast.getId()).orElseThrow().getStatus()).isEqualTo(BroadcastStatus.CONFIRMED);
    }

    @Test
    void consentRevokedBetweenDraftAndConfirmExcludesTheRecipientWithReason() throws Exception {
        Person director = director("lc-consent-director");
        Person revoked = person("lc-consent-a", true, "PRECEPTOR");
        Person kept = person("lc-consent-b", true, "PRECEPTOR");
        Broadcast broadcast = draft(director);
        transactionTemplate.executeWithoutResult(tx -> contactConsentLedger.recordConsent(revoked.getId(), ConsentChannel.EMAIL,
                BroadcastCategory.INSTITUTIONAL, false, ConsentSource.EMAIL_LINK, "baja antes de confirmar"));

        confirm(broadcast, director);

        assertThat(recipientStatus(broadcast, revoked)).isEqualTo(BroadcastRecipientStatus.EXCLUDED);
        assertThat(count("select count(*) from broadcast_recipient where broadcast_id = ? and person_id = ? and exclusion_reason = ?",
                broadcast.getId(), revoked.getId(), ExclusionReason.OPTED_OUT.name())).isEqualTo(1);
        assertThat(deliveriesOf(revoked, "QUEUED")).isZero();
        assertThat(recipientStatus(broadcast, kept)).isEqualTo(BroadcastRecipientStatus.QUEUED);
        assertThat(deliveriesOf(kept, "QUEUED")).isEqualTo(1);
    }

    @Test
    void cancelWithPartialSendKeepsTheSentRecipientsAsSent() throws Exception {
        Person director = director("lc-partial-director");
        Person sent = person("lc-partial-a", true, "PRECEPTOR");
        Person waitingOne = person("lc-partial-b", true, "PRECEPTOR");
        Person waitingTwo = person("lc-partial-c", true, "PRECEPTOR");
        Broadcast broadcast = draft(director);
        confirm(broadcast, director);
        jdbc.update("update notification_delivery set status = 'SENT', sent_at = now() where recipient_person_id = ? and related_entity_type = 'BROADCAST'", sent.getId());

        cancel(broadcast, director, 200, null);

        assertThat(recipientStatus(broadcast, sent)).isEqualTo(BroadcastRecipientStatus.SENT);
        assertThat(recipientStatus(broadcast, waitingOne)).isEqualTo(BroadcastRecipientStatus.SKIPPED);
        assertThat(recipientStatus(broadcast, waitingTwo)).isEqualTo(BroadcastRecipientStatus.SKIPPED);
        assertThat(deliveriesOf(sent, "SENT")).isEqualTo(1);
        assertThat(deliveriesOf(sent, "DISCARDED")).isZero();
        assertThat(deliveriesOf(waitingOne, "DISCARDED")).isEqualTo(1);
        assertThat(deliveriesOf(waitingTwo, "DISCARDED")).isEqualTo(1);
        mockMvc.perform(get("/broadcasts/" + broadcast.getId()).header("Authorization", bearer(director)))
                .andExpect(jsonPath("$.recipientCounts.SENT").value(1))
                .andExpect(jsonPath("$.recipientCounts.SKIPPED").value(2));
    }

    @Test
    void cancelFromConfirmedAndFromSendingIsAllowed() throws Exception {
        Person director = director("lc-cancel-states-director");
        person("lc-cancel-states-a", true, "PRECEPTOR");
        Broadcast confirmed = draft(director);
        confirm(confirmed, director);
        Broadcast sending = draft(director);
        confirm(sending, director);
        forceStatus(sending, "SENDING");

        cancel(confirmed, director, 200, null);
        cancel(sending, director, 200, null);

        assertThat(broadcastRepository.findById(confirmed.getId()).orElseThrow().getStatus()).isEqualTo(BroadcastStatus.CANCELLED);
        assertThat(broadcastRepository.findById(sending.getId()).orElseThrow().getStatus()).isEqualTo(BroadcastStatus.CANCELLED);
    }

    @Test
    void cancelledBroadcastRejectsConfirmAndCancelWithTheExactCode() throws Exception {
        Person director = director("lc-cancelled-director");
        Broadcast broadcast = draft(director);
        cancel(broadcast, director, 200, null);

        mockMvc.perform(post("/broadcasts/" + broadcast.getId() + "/confirm").header("Authorization", bearer(director)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ILLEGAL_BROADCAST_STATUS_TRANSITION"));
        cancel(broadcast, director, 409, "ILLEGAL_BROADCAST_STATUS_TRANSITION");
    }

    @Test
    void completedBroadcastCannotBeCancelledAndConfirmDoesNotRequeue() throws Exception {
        Person director = director("lc-completed-director");
        Person eligible = person("lc-completed-a", true, "PRECEPTOR");
        Broadcast broadcast = draft(director);
        confirm(broadcast, director);
        forceStatus(broadcast, "COMPLETED");

        cancel(broadcast, director, 409, "ILLEGAL_BROADCAST_STATUS_TRANSITION");
        confirm(broadcast, director);

        assertThat(broadcastRepository.findById(broadcast.getId()).orElseThrow().getStatus()).isEqualTo(BroadcastStatus.COMPLETED);
        assertThat(deliveriesOf(eligible, "QUEUED")).isEqualTo(1);
    }

    @Test
    void teacherCannotConfirmCancelOrRead() throws Exception {
        Person teacher = person("lc-teacher", true, "DOCENTE");
        Broadcast broadcast = draft(director("lc-forbidden-director"));

        mockMvc.perform(post("/broadcasts/" + broadcast.getId() + "/confirm").header("Authorization", bearer(teacher))).andExpect(status().isForbidden());
        mockMvc.perform(post("/broadcasts/" + broadcast.getId() + "/cancel").header("Authorization", bearer(teacher))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Motivo\"}")).andExpect(status().isForbidden());
        mockMvc.perform(get("/broadcasts/" + broadcast.getId()).header("Authorization", bearer(teacher))).andExpect(status().isForbidden());
        mockMvc.perform(get("/broadcasts").header("Authorization", bearer(teacher))).andExpect(status().isForbidden());
        mockMvc.perform(get("/broadcasts/" + broadcast.getId() + "/recipients").header("Authorization", bearer(teacher))).andExpect(status().isForbidden());
        assertThat(broadcastRepository.findById(broadcast.getId()).orElseThrow().getStatus()).isEqualTo(BroadcastStatus.DRAFT);
    }

    @Test
    void registryShowsDetailWithCountsAndRecipients() throws Exception {
        Person director = director("lc-read-director");
        person("lc-read-a", true, "PRECEPTOR");
        Person unverified = person("lc-read-b", false, "PRECEPTOR");
        Broadcast broadcast = draft(director);
        confirm(broadcast, director);

        mockMvc.perform(get("/broadcasts").header("Authorization", bearer(director)).param("status", "CONFIRMED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(broadcast.getId()));
        mockMvc.perform(get("/broadcasts/" + broadcast.getId()).header("Authorization", bearer(director)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipientCounts.QUEUED").value(1))
                .andExpect(jsonPath("$.recipientCounts.EXCLUDED").value(1))
                .andExpect(jsonPath("$.subject").value("Asunto"));
        mockMvc.perform(get("/broadcasts/" + broadcast.getId() + "/recipients").header("Authorization", bearer(director)).param("status", "EXCLUDED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.personId == " + unverified.getId() + ")].exclusionReason").value("NO_VERIFIED_EMAIL"));
        mockMvc.perform(get("/broadcasts/999999999").header("Authorization", bearer(director))).andExpect(status().isNotFound());
        mockMvc.perform(get("/broadcasts/999999999/recipients").header("Authorization", bearer(director))).andExpect(status().isNotFound());
    }
}
