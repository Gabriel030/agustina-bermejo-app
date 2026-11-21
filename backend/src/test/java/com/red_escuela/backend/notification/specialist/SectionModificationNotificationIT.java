package com.red_escuela.backend.notification.specialist;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.events.SectionModificationApprovedEvent;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.AuthProvider;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.notification.entity.Notification;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.notification.repository.NotificationRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.event.CorrelationIds;
import com.red_escuela.backend.testsupport.SlowTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static com.red_escuela.backend.testsupport.AsyncNotifications.awaitAsserted;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@SlowTest(reason = "Waits for asynchronous notification listeners after committed section modification requests")
class SectionModificationNotificationIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Person requester;
    private Person otherPreceptor;
    private Person director;
    private Person otherDirector;
    private Long sectionId;
    private Long classroomId;
    private String sectionLabel;

    @BeforeEach
    void persistScenario() {
        long suffix = System.nanoTime();
        transactionTemplate.executeWithoutResult(status -> {
            var fixtures = new CycleTestFixtures(entityManager);
            requester = persistPerson("req" + suffix, "PRECEPTOR");
            otherPreceptor = persistPerson("oth" + suffix, "PRECEPTOR");
            director = persistPerson("dir" + suffix, "DIRECTOR");
            otherDirector = persistPerson("dir2" + suffix, "DIRECTOR");
            var term = fixtures.term(2040 + (int) (suffix % 50));
            var cycle = fixtures.programCycle(fixtures.program(), term, AcademicOfferingCycleStatus.APPROVED);
            SubjectSection section = fixtures.section(fixtures.subject(), term, StatusType.ACTIVE, cycle);
            var classroom = fixtures.classroom(30);
            fixtures.flushAndClear();
            sectionId = section.getId();
            classroomId = classroom.getId();
            sectionLabel = section.getSubject().getName() + " · " + section.getName();
        });
    }

    @Test
    void requestNotifiesEveryDirectorAndNobodyElse() throws Exception {
        requestModification(sectionId).andExpect(status().isNoContent());

        awaitAsserted(() -> {
            assertThat(of(director, NotificationType.SECTION_MODIFICATION_REQUESTED)).singleElement()
                    .satisfies(notification -> assertThat(notification.getMessage()).isEqualTo("Pedido de cambio de horario: " + sectionLabel));
            assertThat(of(otherDirector, NotificationType.SECTION_MODIFICATION_REQUESTED)).hasSize(1);
        });
        assertThat(of(requester, NotificationType.SECTION_MODIFICATION_REQUESTED)).isEmpty();
        assertThat(of(otherPreceptor, NotificationType.SECTION_MODIFICATION_REQUESTED)).isEmpty();
    }

    @Test
    void approvalNotifiesOnlyTheRequester() throws Exception {
        requestModification(sectionId).andExpect(status().isNoContent());

        mockMvc.perform(post("/subject-sections/{id}/modification-request/approve", sectionId).header("Authorization", bearer(director)))
                .andExpect(status().isNoContent());

        awaitAsserted(() -> assertThat(of(requester, NotificationType.SECTION_MODIFICATION_APPROVED)).singleElement()
                .satisfies(notification -> assertThat(notification.getMessage())
                        .isEqualTo("Tu pedido de cambio de horario de " + sectionLabel + " fue aprobado")));
        assertThat(of(otherPreceptor, NotificationType.SECTION_MODIFICATION_APPROVED)).isEmpty();
        assertThat(of(director, NotificationType.SECTION_MODIFICATION_APPROVED)).isEmpty();
    }

    @Test
    void rejectionNotifiesTheRequesterWithTheReason() throws Exception {
        requestModification(sectionId).andExpect(status().isNoContent());

        mockMvc.perform(post("/subject-sections/{id}/modification-request/reject", sectionId).header("Authorization", bearer(director))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"aula reservada\"}"))
                .andExpect(status().isNoContent());

        awaitAsserted(() -> assertThat(of(requester, NotificationType.SECTION_MODIFICATION_REJECTED)).singleElement()
                .satisfies(notification -> assertThat(notification.getMessage())
                        .isEqualTo("Tu pedido de cambio de horario de " + sectionLabel + " fue rechazado. Motivo: aula reservada")));
        assertThat(of(otherPreceptor, NotificationType.SECTION_MODIFICATION_REJECTED)).isEmpty();
    }

    @Test
    void rejectingAnUnknownRequestOrRequestingTwiceDoesNotEmitExtraNotifications() throws Exception {
        mockMvc.perform(post("/subject-sections/{id}/modification-request/reject", sectionId).header("Authorization", bearer(director))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"nada\"}"))
                .andExpect(status().isConflict());
        requestModification(sectionId).andExpect(status().isNoContent());
        awaitAsserted(() -> assertThat(of(director, NotificationType.SECTION_MODIFICATION_REQUESTED)).hasSize(1));

        requestModification(sectionId).andExpect(status().isConflict());

        await().pollDelay(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(of(director, NotificationType.SECTION_MODIFICATION_REQUESTED)).hasSize(1);
            assertThat(of(requester, NotificationType.SECTION_MODIFICATION_REJECTED)).isEmpty();
        });
    }

    @Test
    void aRolledBackPublicationNeverCreatesTheNotification() {
        var event = new SectionModificationApprovedEvent(CorrelationIds.current(), Instant.parse("2027-03-01T10:00:00Z"), 1L, sectionId,
                "Materia", "Comision", requester.getId());

        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(event);
            status.setRollbackOnly();
        });

        await().pollDelay(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(of(requester, NotificationType.SECTION_MODIFICATION_APPROVED)).isEmpty());
    }

    @Test
    void republishingTheSameApprovalNotifiesOnce() {
        var event = new SectionModificationApprovedEvent(CorrelationIds.current(), Instant.parse("2027-03-01T10:00:00Z"), 1L, sectionId,
                "Materia", "Comision", requester.getId());

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));
        awaitAsserted(() -> assertThat(of(requester, NotificationType.SECTION_MODIFICATION_APPROVED)).hasSize(1));
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));

        await().pollDelay(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(of(requester, NotificationType.SECTION_MODIFICATION_APPROVED)).hasSize(1));
    }

    @Test
    void approvingAHistoricalRequestWithoutRequesterNotifiesNobodyAndDrainsTheListener() throws Exception {
        requestModification(sectionId).andExpect(status().isNoContent());
        jdbcTemplate.update("update section_modification_proposal set requested_by_person_id = null where subject_section_id = ?", sectionId);

        mockMvc.perform(post("/subject-sections/{id}/modification-request/approve", sectionId).header("Authorization", bearer(director)))
                .andExpect(status().isNoContent());

        awaitAsserted(() -> assertThat(pendingListenerPublications("notifyApproval")).isZero());
        assertThat(of(requester, NotificationType.SECTION_MODIFICATION_APPROVED)).isEmpty();
        assertThat(of(director, NotificationType.SECTION_MODIFICATION_APPROVED)).isEmpty();
    }

    @Test
    void rejectingAHistoricalRequestWithoutRequesterNotifiesNobodyAndDrainsTheListener() throws Exception {
        requestModification(sectionId).andExpect(status().isNoContent());
        jdbcTemplate.update("update section_modification_proposal set requested_by_person_id = null where subject_section_id = ?", sectionId);

        mockMvc.perform(post("/subject-sections/{id}/modification-request/reject", sectionId).header("Authorization", bearer(director))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"aula reservada\"}"))
                .andExpect(status().isNoContent());

        awaitAsserted(() -> assertThat(pendingListenerPublications("notifyRejection")).isZero());
        assertThat(of(requester, NotificationType.SECTION_MODIFICATION_REJECTED)).isEmpty();
        assertThat(of(director, NotificationType.SECTION_MODIFICATION_REJECTED)).isEmpty();
    }

    private Long pendingListenerPublications(String listenerMethod) {
        return jdbcTemplate.queryForObject(
                "select count(*) from event_publication where listener_id like ? and serialized_event ~ ?",
                Long.class, "%" + listenerMethod + "%", "\"sectionId\":" + sectionId + "[,}]");
    }

    private List<Notification> of(Person person, NotificationType type) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(person.getId()).stream()
                .filter(notification -> notification.getType() == type).toList();
    }

    private org.springframework.test.web.servlet.ResultActions requestModification(Long id) throws Exception {
        var body = "{\"slots\":[{\"classroomId\":" + classroomId + ",\"dayOfWeek\":\"FRIDAY\",\"startTime\":\"10:00\",\"endTime\":\"12:00\"}]}";
        return mockMvc.perform(post("/subject-sections/{id}/modification-request", id)
                .header("Authorization", bearer(requester)).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String bearer(Person person) {
        return "Bearer " + jwtService.generateAccessToken(person);
    }

    private Person persistPerson(String username, String roleName) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setName("Notif");
        person.setLastname(username);
        person.setUsername(username);
        person.setDni(username);
        person.setEmail(username + "@example.com");
        person.setPassword("not-used");
        person.setAuthProvider(AuthProvider.LOCAL);
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        person.getRoles().add(roleRepository.findByName(roleName).orElseThrow());
        entityManager.persist(person);
        entityManager.flush();
        return person;
    }
}
