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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
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
        return recipientRepository.findByBroadcastIdAndStatusIn(broadcast.getId(), java.util.List.of(BroadcastRecipientStatus.values())).stream()
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
                .andExpect(jsonPath("$.items[?(@.id == " + broadcast.getId() + ")]").isNotEmpty());
        mockMvc.perform(get("/broadcasts/" + broadcast.getId()).header("Authorization", bearer(director)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipientCounts.QUEUED").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.recipientCounts.EXCLUDED").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.subject").value("Asunto"));
        mockMvc.perform(get("/broadcasts/" + broadcast.getId() + "/recipients").header("Authorization", bearer(director)).param("status", "EXCLUDED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.personId == " + unverified.getId() + ")].exclusionReason").value("NO_VERIFIED_EMAIL"));
        mockMvc.perform(get("/broadcasts/999999999").header("Authorization", bearer(director))).andExpect(status().isNotFound());
        mockMvc.perform(get("/broadcasts/999999999/recipients").header("Authorization", bearer(director))).andExpect(status().isNotFound());
    }
}
