package com.red_escuela.backend.academicstructure.controller;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycle;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.entity.Classroom;
import com.red_escuela.backend.academicstructure.entity.ProposedSlot;
import com.red_escuela.backend.academicstructure.entity.SectionModificationProposal;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.service.SectionModificationRequestLister;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.AuthProvider;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.persistence.QueryCountAssertions;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SectionModificationRequestsPendingListIT extends AbstractIntegrationTest {

    private static final String PENDING_URL = "/section-modification-requests/pending";

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
    private SectionModificationRequestLister requestLister;

    private CycleTestFixtures fixtures;
    private Person director;
    private Term term;
    private AcademicOfferingCycle approvedCycle;
    private Classroom classroom;

    @BeforeEach
    void persistScenario() {
        fixtures = new CycleTestFixtures(entityManager);
        director = persistPerson("pending.director", "94000001", "DIRECTOR");
        term = fixtures.term(2040 + (int) Math.floorMod(System.nanoTime(), 50L));
        approvedCycle = fixtures.programCycle(fixtures.program(), term, AcademicOfferingCycleStatus.APPROVED);
        classroom = fixtures.classroom(30);
    }

    private SubjectSection sectionIn(AcademicOfferingCycle cycle) {
        return fixtures.section(fixtures.subject(), term, StatusType.ACTIVE, cycle);
    }

    private SectionModificationProposal persistPending(SubjectSection section, Person requester, LocalDateTime requestedAt, boolean replaces) {
        var proposedSlot = new ProposedSlot(classroom, DayOfWeek.FRIDAY, LocalTime.of(9, 0), LocalTime.of(11, 0));
        var proposal = SectionModificationProposal.pending(section, replaces, List.of(proposedSlot), requester, requestedAt);
        entityManager.persist(proposal);
        return proposal;
    }

    private ResultActions listAs(Person person) throws Exception {
        return mockMvc.perform(get(PENDING_URL).header("Authorization", "Bearer " + jwtService.generateAccessToken(person)));
    }

    @Test
    void directorGetsEmptyListWhenThereAreNoPendingRequests() throws Exception {
        fixtures.flushAndClear();
        listAs(director).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void preceptorTeacherAndStudentAreForbidden() throws Exception {
        var preceptor = persistPerson("pending.preceptor", "94000002", "PRECEPTOR");
        var teacher = persistPerson("pending.teacher", "94000003", "DOCENTE");
        var student = persistPerson("pending.student", "94000004", "ALUMNO");
        fixtures.flushAndClear();
        listAs(preceptor).andExpect(status().isForbidden());
        listAs(teacher).andExpect(status().isForbidden());
        listAs(student).andExpect(status().isForbidden());
    }

    @Test
    void directorSeesPendingRequestsOldestFirstWithAndWithoutRequester() throws Exception {
        var requester = fixtures.person();
        var olderSection = sectionIn(approvedCycle);
        var newerSection = sectionIn(approvedCycle);
        fixtures.slot(olderSection, classroom, DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(20, 0));
        persistPending(newerSection, null, LocalDateTime.of(2026, 11, 5, 10, 0), false);
        persistPending(olderSection, requester, LocalDateTime.of(2026, 11, 1, 10, 0), true);
        fixtures.flushAndClear();

        listAs(director).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].sectionId").value(olderSection.getId()))
                .andExpect(jsonPath("$[0].sectionName").value(olderSection.getName()))
                .andExpect(jsonPath("$[0].subjectName").value(olderSection.getSubject().getName()))
                .andExpect(jsonPath("$[0].offeringName").value(approvedCycle.getProgram().getName()))
                .andExpect(jsonPath("$[0].cycleId").value(approvedCycle.getId()))
                .andExpect(jsonPath("$[0].requestedByName").value(requester.getName() + " " + requester.getLastname()))
                .andExpect(jsonPath("$[0].requestedAt").value("2026-11-01T10:00"))
                .andExpect(jsonPath("$[0].replacesSchedule").value(true))
                .andExpect(jsonPath("$[0].currentSlots", hasSize(1)))
                .andExpect(jsonPath("$[0].currentSlots[0].dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$[0].currentSlots[0].classroomId").value(classroom.getId()))
                .andExpect(jsonPath("$[0].proposedSlots", hasSize(1)))
                .andExpect(jsonPath("$[0].proposedSlots[0].dayOfWeek").value("FRIDAY"))
                .andExpect(jsonPath("$[0].proposedSlots[0].startTime").value("09:00"))
                .andExpect(jsonPath("$[1].sectionId").value(newerSection.getId()))
                .andExpect(jsonPath("$[1].requestedByName").value("—"))
                .andExpect(jsonPath("$[1].replacesSchedule").value(false))
                .andExpect(jsonPath("$[1].currentSlots", hasSize(0)));
    }

    @Test
    void onlyPendingProposalsOfApprovedCyclesAreListed() throws Exception {
        var pendingSection = sectionIn(approvedCycle);
        var approvedSection = sectionIn(approvedCycle);
        var rejectedSection = sectionIn(approvedCycle);
        var draftCycle = fixtures.programCycle(fixtures.program(), term, AcademicOfferingCycleStatus.DRAFT);
        var draftCycleSection = sectionIn(draftCycle);
        var base = LocalDateTime.of(2026, 11, 1, 10, 0);
        persistPending(pendingSection, null, base, false);
        persistPending(approvedSection, null, base.plusHours(1), false).approve(base.plusDays(1));
        persistPending(rejectedSection, null, base.plusHours(2), false).reject("no", base.plusDays(1));
        persistPending(draftCycleSection, null, base.plusHours(3), false);
        fixtures.flushAndClear();

        listAs(director).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].sectionId").value(pendingSection.getId()));
    }

    @Test
    void listingManyRequestsDoesNotRunOneQueryPerRequest() {
        var requester = fixtures.person();
        for (int index = 0; index < 5; index++) {
            var section = sectionIn(approvedCycle);
            fixtures.slot(section, fixtures.classroom(30), DayOfWeek.MONDAY, LocalTime.of(18, 0), LocalTime.of(20, 0));
            persistPending(section, requester, LocalDateTime.of(2026, 11, 1 + index, 10, 0), true);
        }
        fixtures.flushAndClear();

        var responses = QueryCountAssertions.assertAtMostQueries(
                entityManagerFactory, 2, requestLister::listPendingRequests);

        assertThat(responses).hasSize(5);
    }

    private Person persistPerson(String username, String dni, String roleName) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setName("Pend");
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
