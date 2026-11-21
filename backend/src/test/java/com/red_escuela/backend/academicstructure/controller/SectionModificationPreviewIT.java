package com.red_escuela.backend.academicstructure.controller;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycle;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.AuthProvider;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalTime;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SectionModificationPreviewIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private com.red_escuela.backend.academicstructure.repository.SectionModificationProposalRepository proposalRepository;

    private CycleTestFixtures fixtures;
    private Person preceptor;
    private Person student;
    private SubjectSection section;
    private SubjectSection morningSection;
    private SubjectSection occupyingSection;
    private Long ownClassroomId;
    private Long occupiedClassroomId;
    private Long freeClassroomId;
    private Long smallClassroomId;
    private AcademicOfferingCycle cycle;

    @BeforeEach
    void persistScenario() {
        fixtures = new CycleTestFixtures(entityManager);
        preceptor = persistPerson("preview.preceptor", "93000001", "PRECEPTOR");
        student = persistPerson("preview.student", "93000002", "ALUMNO");
        var term = fixtures.term(2040 + (int) (System.nanoTime() % 50));
        cycle = fixtures.programCycle(fixtures.program(), term, AcademicOfferingCycleStatus.APPROVED);
        section = fixtures.section(fixtures.subject(), term, StatusType.ACTIVE, cycle);
        occupyingSection = fixtures.section(fixtures.subject(), term, StatusType.ACTIVE, cycle);
        morningSection = fixtures.section(fixtures.subject(), term, StatusType.ACTIVE, cycle);
        morningSection.setShift(com.red_escuela.backend.academicstructure.domain.SectionShift.MORNING);
        var busyInstructorSection = fixtures.section(fixtures.subject(), term, StatusType.ACTIVE, cycle);
        var own = fixtures.classroom(30);
        var occupied = fixtures.classroom(30);
        var free = fixtures.classroom(30);
        var small = fixtures.classroom(5);
        var instructor = fixtures.instructor();
        fixtures.assignLead(section, instructor);
        fixtures.assignLead(busyInstructorSection, instructor);
        fixtures.slot(section, own, DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(20, 0));
        fixtures.slot(occupyingSection, occupied, DayOfWeek.TUESDAY, LocalTime.of(10, 0), LocalTime.of(12, 0));
        fixtures.slot(busyInstructorSection, free, DayOfWeek.WEDNESDAY, LocalTime.of(10, 0), LocalTime.of(12, 0));
        ownClassroomId = own.getId();
        occupiedClassroomId = occupied.getId();
        freeClassroomId = free.getId();
        smallClassroomId = small.getId();
        fixtures.flushAndClear();
    }

    private String slot(Long classroomId, String day, String start, String end) {
        return "{\"classroomId\":" + classroomId + ",\"dayOfWeek\":\"" + day + "\",\"startTime\":\"" + start + "\",\"endTime\":\"" + end + "\"}";
    }

    private String body(String... slots) {
        return "{\"slots\":[" + String.join(",", slots) + "]}";
    }

    private org.springframework.test.web.servlet.ResultActions preview(Long sectionId, String body) throws Exception {
        return mockMvc.perform(post("/subject-sections/{id}/modification-request/preview", sectionId)
                .header("Authorization", bearer(preceptor)).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private org.springframework.test.web.servlet.ResultActions request(Long sectionId, String body) throws Exception {
        return mockMvc.perform(post("/subject-sections/{id}/modification-request", sectionId)
                .header("Authorization", bearer(preceptor)).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void setCycleStatus(AcademicOfferingCycleStatus status) {
        entityManager.createQuery("update AcademicOfferingCycle c set c.status = :status where c.id = :id")
                .setParameter("status", status).setParameter("id", cycle.getId()).executeUpdate();
        entityManager.clear();
    }

    @Test
    void previewReportsEveryConflictTypeWithTheOccupyingSectionLabel() throws Exception {
        var occupyingLabel = occupyingSection.getSubject().getName() + " · " + occupyingSection.getName();
        preview(section.getId(), body(
                slot(occupiedClassroomId, "TUESDAY", "10:00", "12:00"),
                slot(smallClassroomId, "FRIDAY", "10:00", "12:00"),
                slot(ownClassroomId, "WEDNESDAY", "10:00", "12:00"),
                slot(ownClassroomId, "THURSDAY", "10:00", "12:00"),
                slot(ownClassroomId, "THURSDAY", "11:00", "13:00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conflicts", hasSize(4)))
                .andExpect(jsonPath("$.conflicts[0].slotIndex").value(0))
                .andExpect(jsonPath("$.conflicts[0].type").value("CLASSROOM_OCCUPIED"))
                .andExpect(jsonPath("$.conflicts[0].conflictingSectionLabel").value(occupyingLabel))
                .andExpect(jsonPath("$.conflicts[1].slotIndex").value(1))
                .andExpect(jsonPath("$.conflicts[1].type").value("CLASSROOM_CAPACITY_EXCEEDED"))
                .andExpect(jsonPath("$.conflicts[2].slotIndex").value(2))
                .andExpect(jsonPath("$.conflicts[2].type").value("INSTRUCTOR_BUSY"))
                .andExpect(jsonPath("$.conflicts[3].slotIndex").value(4))
                .andExpect(jsonPath("$.conflicts[3].type").value("SLOTS_OVERLAP"));
    }

    @Test
    void previewWithAdjacentSlotsAndFreeClassroomHasNoConflicts() throws Exception {
        preview(section.getId(), body(
                slot(occupiedClassroomId, "TUESDAY", "12:00", "14:00"),
                slot(occupiedClassroomId, "TUESDAY", "08:00", "10:00"),
                slot(ownClassroomId, "MONDAY", "20:00", "21:00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conflicts", hasSize(0)));
    }

    @Test
    void previewWithoutSlotsIsABadRequest() throws Exception {
        preview(section.getId(), "{}").andExpect(status().isBadRequest());
    }

    @Test
    void previewOfASectionWhoseCycleIsClosedIsConflict() throws Exception {
        setCycleStatus(AcademicOfferingCycleStatus.CLOSED);
        preview(section.getId(), body(slot(freeClassroomId, "FRIDAY", "10:00", "12:00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SECTION_MODIFICATION_CYCLE_CLOSED"));
    }

    @Test
    void requestForASectionWhoseCycleIsNotApprovedIsConflictBeforeAnyOtherValidation() throws Exception {
        setCycleStatus(AcademicOfferingCycleStatus.CLOSED);
        request(section.getId(), body(slot(smallClassroomId, "FRIDAY", "10:00", "12:00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SECTION_MODIFICATION_CYCLE_CLOSED"));
        setCycleStatus(AcademicOfferingCycleStatus.PENDING_APPROVAL);
        request(section.getId(), body(slot(freeClassroomId, "FRIDAY", "10:00", "12:00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SECTION_MODIFICATION_CYCLE_CLOSED"));
    }

    @Test
    void validRequestInAnApprovedCycleIsAccepted() throws Exception {
        request(section.getId(), body(slot(freeClassroomId, "FRIDAY", "10:00", "12:00")))
                .andExpect(status().isNoContent());
    }

    @Test
    void requestWithoutSlotsKeepsBeingAcceptedAsAScheduleThatDoesNotChange() throws Exception {
        request(section.getId(), "{}").andExpect(status().isNoContent());
    }

    @Test
    void requestErrorCodeMatchesTheTypeOfTheFirstPreviewConflictForTheSameDraft() throws Exception {
        var errorCodeByType = java.util.Map.of(
                "SLOT_OUTSIDE_SHIFT", "SLOT_OUTSIDE_SHIFT",
                "CLASSROOM_CAPACITY_EXCEEDED", "SECTION_CAPACITY_EXCEEDS_CLASSROOM",
                "CLASSROOM_OCCUPIED", "SECTION_SCHEDULE_CONFLICT",
                "INSTRUCTOR_BUSY", "INSTRUCTOR_SCHEDULE_CONFLICT",
                "SLOTS_OVERLAP", "SECTION_SCHEDULE_CONFLICT");
        record Scenario(Long sectionId, String json, String expectedType) {}
        var scenarios = java.util.List.of(
                new Scenario(morningSection.getId(), body(slot(freeClassroomId, "FRIDAY", "18:00", "20:00")), "SLOT_OUTSIDE_SHIFT"),
                new Scenario(section.getId(), body(slot(smallClassroomId, "FRIDAY", "10:00", "12:00")), "CLASSROOM_CAPACITY_EXCEEDED"),
                new Scenario(section.getId(), body(slot(occupiedClassroomId, "TUESDAY", "11:00", "13:00")), "CLASSROOM_OCCUPIED"),
                new Scenario(section.getId(), body(slot(ownClassroomId, "WEDNESDAY", "10:00", "12:00")), "INSTRUCTOR_BUSY"),
                new Scenario(section.getId(), body(slot(ownClassroomId, "THURSDAY", "10:00", "12:00"), slot(ownClassroomId, "THURSDAY", "11:00", "13:00")), "SLOTS_OVERLAP"),
                new Scenario(section.getId(), body(slot(occupiedClassroomId, "TUESDAY", "12:00", "14:00")), null),
                new Scenario(section.getId(), body(slot(ownClassroomId, "THURSDAY", "10:00", "12:00"), slot(ownClassroomId, "THURSDAY", "12:00", "14:00")), null));
        for (var scenario : scenarios) {
            var previewBody = preview(scenario.sectionId(), scenario.json()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            var requestResult = request(scenario.sectionId(), scenario.json()).andReturn().getResponse();
            if (scenario.expectedType() == null) {
                org.assertj.core.api.Assertions.assertThat(previewBody).contains("\"conflicts\":[]");
                org.assertj.core.api.Assertions.assertThat(requestResult.getStatus()).isEqualTo(204);
                entityManager.flush();
                proposalRepository.deleteAll();
                entityManager.flush();
                entityManager.createQuery("update SubjectSection s set s.pendingApproval = false where s.id = :id")
                        .setParameter("id", scenario.sectionId()).executeUpdate();
                entityManager.clear();
            } else {
                String firstType = com.jayway.jsonpath.JsonPath.read(previewBody, "$.conflicts[0].type");
                String errorCode = com.jayway.jsonpath.JsonPath.read(requestResult.getContentAsString(), "$.code");
                org.assertj.core.api.Assertions.assertThat(firstType).isEqualTo(scenario.expectedType());
                org.assertj.core.api.Assertions.assertThat(errorCode).isEqualTo(errorCodeByType.get(firstType));
            }
        }
    }

    @Test
    void sectionWithoutCycleIsConflictInRequestAndPreview() throws Exception {
        var orphan = fixtures.section(fixtures.subject(), section.getTerm(), StatusType.ACTIVE, null);
        fixtures.flushAndClear();
        var json = body(slot(freeClassroomId, "FRIDAY", "10:00", "12:00"));
        request(orphan.getId(), json)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SECTION_MODIFICATION_CYCLE_CLOSED"));
        preview(orphan.getId(), json)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SECTION_MODIFICATION_CYCLE_CLOSED"));
    }

    @Test
    void previewIsForbiddenForStudents() throws Exception {
        mockMvc.perform(post("/subject-sections/{id}/modification-request/preview", section.getId())
                        .header("Authorization", bearer(student)).contentType(MediaType.APPLICATION_JSON)
                        .content(body(slot(freeClassroomId, "FRIDAY", "10:00", "12:00"))))
                .andExpect(status().isForbidden());
    }

    private String bearer(Person person) {
        return "Bearer " + jwtService.generateAccessToken(person);
    }

    private Person persistPerson(String username, String dni, String roleName) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setName("Prev");
        person.setLastname(username);
        person.setUsername(username);
        person.setDni(dni);
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
