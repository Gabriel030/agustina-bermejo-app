package com.red_escuela.backend.enrollment.controller;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycle;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.TeachingMode;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import com.red_escuela.backend.shared.audit.AuditRecord;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class WaitlistActionsIT extends AbstractIntegrationTest {

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2046, 2, 3, 9, 0);
    private static final String VALID_REASON = "{\"reason\":\"Motivo suficiente\"}";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private JwtService jwtService;

    private SubjectSection section;
    private SubjectSectionEnrollment first;
    private SubjectSectionEnrollment second;
    private SubjectSectionEnrollment retaker;

    private void givenQueue(int capacity) {
        CycleTestFixtures fixtures = new CycleTestFixtures(entityManager);
        Program program = fixtures.program();
        Term term = fixtures.term(2046);
        Subject subject = fixtures.subject();
        AcademicOfferingCycle cycle = fixtures.programCycle(program, term, AcademicOfferingCycleStatus.APPROVED);
        section = fixtures.section(subject, term, StatusType.ACTIVE, cycle);
        section.setCapacity(capacity);
        Institution institution = institution();
        persistEnrollment(fixtures, institution, program, section, EnrollmentStatus.ENROLLED, BASE_TIME, null);
        first = persistEnrollment(fixtures, institution, program, section, EnrollmentStatus.WAITLISTED, BASE_TIME.plusHours(1), null);
        second = persistEnrollment(fixtures, institution, program, section, EnrollmentStatus.WAITLISTED, BASE_TIME.plusHours(2), null);
        SubjectSection approvedSection = fixtures.section(subject, term, StatusType.ACTIVE, cycle);
        retaker = persistEnrollment(fixtures, institution, program, section, EnrollmentStatus.WAITLISTED, BASE_TIME.plusHours(3), approvedSection);
        entityManager.flush();
    }

    private long auditCount(String action, Long entityId) {
        return ((Number) entityManager.createNativeQuery("select count(*) from audit_log where action = :a and entity_id = :id")
                .setParameter("a", action).setParameter("id", entityId).getSingleResult()).longValue();
    }

    @Test
    void promoteMovesEntryToEnrolledAndAudits() throws Exception {
        givenQueue(2);
        mockMvc.perform(post("/waitlists/PROGRAM_SECTION/entries/{id}/promote", first.getId())
                        .header("Authorization", "Bearer " + tokenWithRole("admin-promote", "ADMINISTRATIVO")))
                .andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();
        Assertions.assertEquals(EnrollmentStatus.ENROLLED,
                entityManager.find(SubjectSectionEnrollment.class, first.getId()).getStatus());
        Assertions.assertEquals(1, auditCount("WAITLIST_PROMOTED_BY_STAFF", first.getId()));
    }

    @Test
    void promoteWithoutFreeSeatFails() throws Exception {
        givenQueue(1);
        mockMvc.perform(post("/waitlists/PROGRAM_SECTION/entries/{id}/promote", first.getId())
                        .header("Authorization", "Bearer " + tokenWithRole("admin-noseat", "ADMINISTRATIVO")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WAITLIST_NO_FREE_SEAT"));
    }

    @Test
    void prioritizeMovesEntryToFirstPlaceAndAudits() throws Exception {
        givenQueue(1);
        String token = tokenWithRole("director-prio", "DIRECTOR");
        mockMvc.perform(post("/waitlists/PROGRAM_SECTION/entries/{id}/prioritize", second.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_REASON))
                .andExpect(status().isOk());
        entityManager.flush();
        mockMvc.perform(get("/waitlists/{kind}/{id}", "PROGRAM_SECTION", section.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.entries[0].enrollmentId").value(second.getId()))
                .andExpect(jsonPath("$.entries[1].enrollmentId").value(first.getId()));
        Assertions.assertEquals(1, auditCount("WAITLIST_PRIORITIZED_BY_STAFF", second.getId()));
    }

    @Test
    void prioritizeAndRemoveRequireReason() throws Exception {
        givenQueue(1);
        String token = tokenWithRole("director-reason", "DIRECTOR");
        for (String action : new String[]{"prioritize", "remove"}) {
            mockMvc.perform(post("/waitlists/PROGRAM_SECTION/entries/{id}/" + action, first.getId())
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"corto\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("WAITLIST_REASON_REQUIRED"));
        }
    }

    @Test
    void removeWithdrawsEntryAndAudits() throws Exception {
        givenQueue(1);
        mockMvc.perform(post("/waitlists/PROGRAM_SECTION/entries/{id}/remove", first.getId())
                        .header("Authorization", "Bearer " + tokenWithRole("admin-remove", "ADMINISTRATIVO"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_REASON))
                .andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();
        Assertions.assertEquals(EnrollmentStatus.WITHDRAWN,
                entityManager.find(SubjectSectionEnrollment.class, first.getId()).getStatus());
        Assertions.assertEquals(1, auditCount("WAITLIST_REMOVED_BY_STAFF", first.getId()));
    }

    private LocalDateTime prioritizedAtOf(Long id) {
        entityManager.flush();
        entityManager.clear();
        return entityManager.find(SubjectSectionEnrollment.class, id).getWaitlistPrioritizedAt();
    }

    private void prioritize(String token, Long id) throws Exception {
        mockMvc.perform(post("/waitlists/PROGRAM_SECTION/entries/{id}/prioritize", id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_REASON))
                .andExpect(status().isOk());
        entityManager.flush();
    }

    private void assertQueueOrder(String token, Long... expected) throws Exception {
        var result = mockMvc.perform(get("/waitlists/{kind}/{id}", "PROGRAM_SECTION", section.getId())
                .header("Authorization", "Bearer " + token));
        for (int index = 0; index < expected.length; index++) {
            result.andExpect(jsonPath("$.entries[" + index + "].enrollmentId").value(expected[index]));
        }
    }

    @Test
    void lastPrioritizedEntryIsAlwaysFirst() throws Exception {
        givenQueue(1);
        String token = tokenWithRole("director-last", "DIRECTOR");
        prioritize(token, first.getId());
        prioritize(token, second.getId());
        assertQueueOrder(token, second.getId(), first.getId());
        Assertions.assertTrue(prioritizedAtOf(second.getId()).isAfter(prioritizedAtOf(first.getId())));
    }

    @Test
    void prioritizingTheEntryThatIsAlreadyFirstIsIdempotent() throws Exception {
        givenQueue(1);
        String token = tokenWithRole("director-idem", "DIRECTOR");
        prioritize(token, second.getId());
        LocalDateTime firstTimestamp = prioritizedAtOf(second.getId());
        prioritize(token, second.getId());
        prioritize(token, second.getId());
        Assertions.assertEquals(firstTimestamp, prioritizedAtOf(second.getId()));
        Assertions.assertEquals(1, auditCount("WAITLIST_PRIORITIZED_BY_STAFF", second.getId()));
        assertQueueOrder(token, second.getId(), first.getId());
    }

    @Test
    void reprioritizingAnOlderPrioritizedEntryMovesItBackToFirst() throws Exception {
        givenQueue(1);
        String token = tokenWithRole("director-again", "DIRECTOR");
        prioritize(token, first.getId());
        prioritize(token, second.getId());
        prioritize(token, first.getId());
        assertQueueOrder(token, first.getId(), second.getId());
        Assertions.assertEquals(2, auditCount("WAITLIST_PRIORITIZED_BY_STAFF", first.getId()));
    }

    @Test
    void prioritizationBeatsAnExistingTimestampEqualOrInTheFuture() throws Exception {
        givenQueue(1);
        String token = tokenWithRole("director-tie", "DIRECTOR");
        LocalDateTime future = LocalDateTime.now().plusDays(1);
        first.setWaitlistPrioritizedAt(future);
        entityManager.flush();
        prioritize(token, second.getId());
        assertQueueOrder(token, second.getId(), first.getId());
        Assertions.assertTrue(prioritizedAtOf(second.getId()).isAfter(future));
    }

    @Test
    void tiedPrioritizationTimestampsFallBackToJoinOrder() throws Exception {
        givenQueue(1);
        String token = tokenWithRole("director-join", "DIRECTOR");
        LocalDateTime same = BASE_TIME.plusDays(1);
        second.setWaitlistPrioritizedAt(same);
        first.setWaitlistPrioritizedAt(same);
        entityManager.flush();
        assertQueueOrder(token, first.getId(), second.getId());
    }

    @Test
    void promotingANotEligibleCandidateFailsAndKeepsItWaiting() throws Exception {
        givenQueue(5);
        mockMvc.perform(post("/waitlists/PROGRAM_SECTION/entries/{id}/promote", retaker.getId())
                        .header("Authorization", "Bearer " + tokenWithRole("admin-noteligible", "ADMINISTRATIVO")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WAITLIST_CANDIDATE_NOT_ELIGIBLE"));
        entityManager.flush();
        entityManager.clear();
        Assertions.assertEquals(EnrollmentStatus.WAITLISTED, entityManager.find(SubjectSectionEnrollment.class, retaker.getId()).getStatus());
        Assertions.assertEquals(0, auditCount("WAITLIST_PROMOTED_BY_STAFF", retaker.getId()));
    }

    @Test
    void promotingTwiceFailsTheSecondTime() throws Exception {
        givenQueue(3);
        String token = tokenWithRole("admin-twice", "ADMINISTRATIVO");
        mockMvc.perform(post("/waitlists/PROGRAM_SECTION/entries/{id}/promote", first.getId())
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        entityManager.flush();
        mockMvc.perform(post("/waitlists/PROGRAM_SECTION/entries/{id}/promote", first.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WAITLIST_ENTRY_NOT_WAITING"));
        Assertions.assertEquals(1, auditCount("WAITLIST_PROMOTED_BY_STAFF", first.getId()));
    }

    @Test
    void removingTwiceFailsTheSecondTime() throws Exception {
        givenQueue(1);
        String token = tokenWithRole("admin-remove-twice", "ADMINISTRATIVO");
        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post("/waitlists/PROGRAM_SECTION/entries/{id}/remove", first.getId())
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(VALID_REASON))
                    .andExpect(status().is(attempt == 0 ? 200 : 409));
            entityManager.flush();
        }
        Assertions.assertEquals(1, auditCount("WAITLIST_REMOVED_BY_STAFF", first.getId()));
    }

    @Test
    void preceptorCannotPromote() throws Exception {
        givenQueue(2);
        mockMvc.perform(post("/waitlists/PROGRAM_SECTION/entries/{id}/promote", first.getId())
                        .header("Authorization", "Bearer " + tokenWithRole("preceptor-promote", "PRECEPTOR")))
                .andExpect(status().isForbidden());
    }

    private Institution institution() {
        Institution institution = new Institution();
        institution.setName("Instituto");
        entityManager.persist(institution);
        return institution;
    }

    private Student student(CycleTestFixtures fixtures, Institution institution) {
        Student student = new Student();
        student.setPerson(fixtures.person());
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        return student;
    }

    private SubjectSectionEnrollment persistEnrollment(CycleTestFixtures fixtures, Institution institution, Program program,
                                                       SubjectSection section, EnrollmentStatus status, LocalDateTime enrolledAt,
                                                       SubjectSection alreadyApprovedIn) {
        Student student = student(fixtures, institution);
        ProgramEnrollment programEnrollment = new ProgramEnrollment();
        programEnrollment.setStudent(student);
        programEnrollment.setProgram(program);
        programEnrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        programEnrollment.setEnrollmentDate(LocalDateTime.now());
        entityManager.persist(programEnrollment);
        if (alreadyApprovedIn != null) {
            persistSectionEnrollment(student, programEnrollment, alreadyApprovedIn, EnrollmentStatus.APPROVED, BASE_TIME.minusYears(1));
        }
        return persistSectionEnrollment(student, programEnrollment, section, status, enrolledAt);
    }

    private SubjectSectionEnrollment persistSectionEnrollment(Student student, ProgramEnrollment programEnrollment,
                                                              SubjectSection section, EnrollmentStatus status,
                                                              LocalDateTime enrolledAt) {
        SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
        enrollment.setProgramEnrollment(programEnrollment);
        enrollment.setStudent(student);
        enrollment.setSubjectSection(section);
        enrollment.setStatus(status);
        enrollment.setEnrolledAt(enrolledAt);
        enrollment.setAttemptNumber(1);
        entityManager.persist(enrollment);
        return enrollment;
    }

    private String tokenWithRole(String username, String roleName) {
        Person person = new CycleTestFixtures(entityManager).person();
        person.setUsername(username);
        Role role = roleRepository.findByName(roleName).orElseThrow();
        person.getRoles().add(role);
        entityManager.flush();
        return jwtService.generateAccessToken(person);
    }
}
