package com.red_escuela.backend.notification.broadcast.lifecycle;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.domain.ChannelType;
import com.red_escuela.backend.notification.broadcast.draft.AudienceFreezeFixtures;
import com.red_escuela.backend.notification.broadcast.draft.domain.BroadcastStatus;
import com.red_escuela.backend.notification.broadcast.draft.entity.Broadcast;
import com.red_escuela.backend.notification.broadcast.draft.repository.BroadcastRepository;
import com.red_escuela.backend.notification.broadcast.lifecycle.usecase.ListBroadcastsUseCase;
import com.red_escuela.backend.shared.time.MutableClock;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class BroadcastRegistryReadIT extends AbstractIntegrationTest {

    private static final String SPEC = "{\"version\":1,\"quantifier\":\"ALL\",\"criteria\":[{\"key\":\"STAFF_ROLE\",\"value\":\"PRECEPTOR\"}]}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private BroadcastRepository broadcastRepository;

    @Autowired
    private ListBroadcastsUseCase listBroadcastsUseCase;

    private Person person(String username, String role) {
        String unique = username + System.nanoTime();
        return transactionTemplate.execute(status -> AudienceFreezeFixtures.person(entityManager, roleRepository, unique, true, role));
    }

    private String bearer(Person person) {
        return "Bearer " + jwtService.generateAccessToken(person);
    }

    private Broadcast draft(Person creator) {
        return broadcastRepository.saveAndFlush(Broadcast.builder().name("Aviso").category(BroadcastCategory.INSTITUTIONAL)
                .channel(ChannelType.EMAIL).status(BroadcastStatus.DRAFT).segmentSpec(SPEC).subjectSnapshot("Asunto")
                .bodySnapshot("Cuerpo").createdByPersonId(creator.getId()).createdAt(Instant.now(MutableClock.INSTANCE)).build());
    }

    private void confirm(Broadcast broadcast, Person actor) throws Exception {
        mockMvc.perform(post("/broadcasts/" + broadcast.getId() + "/confirm").header("Authorization", bearer(actor))).andExpect(status().isOk());
    }

    @Test
    void detailShowsCreatorConfirmerAndEstimatedDateForPendingRecipients() throws Exception {
        MutableClock.INSTANCE.reset();
        Person creator = person("rr-creator", "DIRECTOR");
        Person confirmer = person("rr-confirmer", "DIRECTOR");
        person("rr-recipient", "PRECEPTOR");
        Broadcast broadcast = draft(creator);
        confirm(broadcast, confirmer);

        mockMvc.perform(get("/broadcasts/" + broadcast.getId()).header("Authorization", bearer(creator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdByName").value(creator.getName() + " " + creator.getLastname()))
                .andExpect(jsonPath("$.confirmedByName").value(confirmer.getName() + " " + confirmer.getLastname()))
                .andExpect(jsonPath("$.estimatedCompletionDate").value(not(emptyOrNullString())));
    }

    @Test
    void detailHasNoEstimatedDateForDraftsAndCancelledBroadcasts() throws Exception {
        Person director = person("rr-null-director", "DIRECTOR");
        person("rr-null-recipient", "PRECEPTOR");
        Broadcast draft = draft(director);
        Broadcast cancelled = draft(director);
        confirm(cancelled, director);
        mockMvc.perform(post("/broadcasts/" + cancelled.getId() + "/cancel").header("Authorization", bearer(director))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Motivo\"}")).andExpect(status().isOk());

        mockMvc.perform(get("/broadcasts/" + draft.getId()).header("Authorization", bearer(director)))
                .andExpect(jsonPath("$.estimatedCompletionDate").doesNotExist())
                .andExpect(jsonPath("$.confirmedByName").doesNotExist())
                .andExpect(jsonPath("$.createdByName").value(director.getName() + " " + director.getLastname()));
        mockMvc.perform(get("/broadcasts/" + cancelled.getId()).header("Authorization", bearer(director)))
                .andExpect(jsonPath("$.estimatedCompletionDate").doesNotExist());
    }

    @Test
    void listShowsNamesAndCountersPerRow() throws Exception {
        Person director = person("rr-list-director", "DIRECTOR");
        person("rr-list-recipient", "PRECEPTOR");
        Broadcast broadcast = draft(director);
        confirm(broadcast, director);

        mockMvc.perform(get("/broadcasts").header("Authorization", bearer(director)).param("status", "CONFIRMED").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id == " + broadcast.getId() + ")].createdByName").value(director.getName() + " " + director.getLastname()))
                .andExpect(jsonPath("$.items[?(@.id == " + broadcast.getId() + ")].confirmedByName").value(director.getName() + " " + director.getLastname()))
                .andExpect(jsonPath("$.items[?(@.id == " + broadcast.getId() + ")].recipientCounts.QUEUED").value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.greaterThanOrEqualTo(1))));
    }

    @Test
    void listQueryCountDoesNotGrowWithTheNumberOfBroadcasts() {
        Person director = person("rr-count-director", "DIRECTOR");
        draft(director);
        entityManager.clear();
        long withOne = statementsToList();
        for (int i = 0; i < 6; i++) {
            draft(person("rr-count-other" + i, "DIRECTOR"));
        }
        entityManager.clear();
        long withMany = statementsToList();

        assertThat(withMany).isEqualTo(withOne);
    }

    private long statementsToList() {
        var statistics = entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        listBroadcastsUseCase.listBroadcasts(null, 0, 100);
        long count = statistics.getPrepareStatementCount();
        statistics.setStatisticsEnabled(false);
        return count;
    }
}
