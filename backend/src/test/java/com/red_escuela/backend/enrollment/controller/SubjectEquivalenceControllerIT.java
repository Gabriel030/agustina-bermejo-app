package com.red_escuela.backend.enrollment.controller;

import com.red_escuela.backend.academicstructure.domain.CurricularTerm;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.config.TestSubjectCodes;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectEquivalence;
import com.red_escuela.backend.enrollment.entity.SubjectEquivalenceStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.events.SubjectEquivalenceRecordedEvent;
import com.red_escuela.backend.enrollment.events.SubjectEquivalenceVoidedEvent;
import com.red_escuela.backend.enrollment.repository.SubjectEquivalenceRepository;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.testsupport.TermFixtures;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@RecordApplicationEvents
class SubjectEquivalenceControllerIT extends AbstractIntegrationTest {

    private static final String REASON = "Estudios previos reconocidos por resolución";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private SubjectEquivalenceRepository equivalenceRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ApplicationEvents events;

    private Student student;
    private Program program;
    private Subject subject;
    private ProgramEnrollment enrollment;
    private static final AtomicLong DNI_SEQUENCE = new AtomicLong(63_000_000L);

    private int staffCounter;

    @BeforeEach
    void seed() {
        Institution institution = new Institution();
        institution.setName("Instituto de prueba");
        entityManager.persist(institution);
        student = persistStudent("61000001", institution);
        program = new Program();
        program.setName("Carrera equivalencias");
        program.setTotalYears(3);
        program.setTotalCreditsRequired(10);
        entityManager.persist(program);
        subject = persistSubjectInPlan(program, "Materia A");
        enrollment = persistEnrollment(student, program, ProgramEnrollmentStatus.ENROLLED);
        entityManager.flush();
    }

    @Test
    void administrativeRecordsEquivalenceAndResponseCarriesOriginAndActor() throws Exception {
        record(token("ADMINISTRATIVO"), body(subject.getId(), "8.50", LocalDate.now().minusDays(1), REASON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.subjectId").value(subject.getId()))
                .andExpect(jsonPath("$.subjectName").value("Materia A"))
                .andExpect(jsonPath("$.credits").value(5))
                .andExpect(jsonPath("$.originScore").value(8.5))
                .andExpect(jsonPath("$.inCurrentPlan").value(true))
                .andExpect(jsonPath("$.recordedBy.personId").isNumber())
                .andExpect(jsonPath("$.voidedBy").doesNotExist());
    }

    @Test
    void directorRecordsEquivalenceWithoutScore() throws Exception {
        record(token("DIRECTOR"), body(subject.getId(), null, LocalDate.now(), REASON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originScore").doesNotExist());
    }

    @Test
    void recordingPersistsAuditWithActorAndReasonAndPublishesEvent() throws Exception {
        record(token("ADMINISTRATIVO"), body(subject.getId(), "7", LocalDate.now(), REASON)).andExpect(status().isCreated());

        SubjectEquivalence saved = equivalenceRepository.findByProgramEnrollmentIdOrderByRecognizedOnDescIdDesc(enrollment.getId()).get(0);
        Map<String, Object> audit = jdbc.queryForMap("select * from audit_log where action = ? and entity_id = ?",
                AuditAction.SUBJECT_EQUIVALENCE_RECORDED.name(), saved.getId());
        assertThat(audit.get("reason")).isEqualTo(REASON);
        assertThat(audit.get("actor_person_id")).isEqualTo(saved.getRecordedById());
        assertThat(events.stream(SubjectEquivalenceRecordedEvent.class).toList())
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.subjectEquivalenceId()).isEqualTo(saved.getId());
                    assertThat(event.studentPersonId()).isEqualTo(student.getPerson().getId());
                    assertThat(event.subjectName()).isEqualTo("Materia A");
                });
    }

    @Test
    void preceptorCannotRecordButCanRead() throws Exception {
        record(token("PRECEPTOR"), body(subject.getId(), null, LocalDate.now(), REASON)).andExpect(status().isForbidden());
        list(token("PRECEPTOR"), "ACTIVE").andExpect(status().isOk());
    }

    @Test
    void docenteAndStudentCannotUseStaffEndpoints() throws Exception {
        record(token("DOCENTE"), body(subject.getId(), null, LocalDate.now(), REASON)).andExpect(status().isForbidden());
        list(token("DOCENTE"), "ACTIVE").andExpect(status().isForbidden());
        list(tokenForStudent(student), "ACTIVE").andExpect(status().isForbidden());
    }

    @Test
    void recordingTwiceForTheSameSubjectReturnsEquivalenceAlreadyActive() throws Exception {
        String token = token("ADMINISTRATIVO");
        record(token, body(subject.getId(), null, LocalDate.now(), REASON)).andExpect(status().isCreated());
        record(token, body(subject.getId(), null, LocalDate.now(), REASON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EQUIVALENCE_ALREADY_ACTIVE"));
    }

    @Test
    void approvedSubjectReturnsSubjectAlreadyApproved() throws Exception {
        persistSectionEnrollment(subject, EnrollmentStatus.APPROVED);
        record(token("ADMINISTRATIVO"), body(subject.getId(), null, LocalDate.now(), REASON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SUBJECT_ALREADY_APPROVED"));
    }

    @Test
    void subjectInProgressReturnsSubjectInProgress() throws Exception {
        persistSectionEnrollment(subject, EnrollmentStatus.IN_PROGRESS);
        record(token("ADMINISTRATIVO"), body(subject.getId(), null, LocalDate.now(), REASON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SUBJECT_IN_PROGRESS"));
    }

    @Test
    void regularSubjectCanReceiveEquivalence() throws Exception {
        persistSectionEnrollment(subject, EnrollmentStatus.REGULAR);
        record(token("ADMINISTRATIVO"), body(subject.getId(), null, LocalDate.now(), REASON))
                .andExpect(status().isCreated());
    }

    @Test
    void inactiveEnrollmentReturnsEnrollmentNotActive() throws Exception {
        enrollment.setStatus(ProgramEnrollmentStatus.PAUSED);
        entityManager.flush();
        record(token("ADMINISTRATIVO"), body(subject.getId(), null, LocalDate.now(), REASON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_NOT_ACTIVE"));
    }

    @Test
    void subjectOutsideThePlanReturnsSubjectNotInPlan() throws Exception {
        Subject outside = persistSubject("Materia ajena");
        record(token("ADMINISTRATIVO"), body(outside.getId(), null, LocalDate.now(), REASON))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SUBJECT_NOT_IN_PLAN"));
    }

    @Test
    void scoreOutOfRangeReturnsInvalidScore() throws Exception {
        String token = token("ADMINISTRATIVO");
        record(token, body(subject.getId(), "10.01", LocalDate.now(), REASON))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_SCORE"));
        record(token, body(subject.getId(), "0.99", LocalDate.now(), REASON))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_SCORE"));
    }

    @Test
    void scoreBoundariesAreAccepted() throws Exception {
        Subject other = persistSubjectInPlan(program, "Materia B");
        entityManager.flush();
        String token = token("ADMINISTRATIVO");
        record(token, body(subject.getId(), "1", LocalDate.now(), REASON)).andExpect(status().isCreated());
        record(token, body(other.getId(), "10", LocalDate.now(), REASON)).andExpect(status().isCreated());
    }

    @Test
    void futureDateReturnsFutureDateAndTodayIsAccepted() throws Exception {
        String token = token("ADMINISTRATIVO");
        record(token, body(subject.getId(), null, LocalDate.now().plusDays(1), REASON))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("FUTURE_DATE"));
        record(token, body(subject.getId(), null, LocalDate.now(), REASON)).andExpect(status().isCreated());
    }

    @Test
    void shortReasonReturnsReasonInvalid() throws Exception {
        record(token("ADMINISTRATIVO"), body(subject.getId(), null, LocalDate.now(), "corto"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EQUIVALENCE_REASON_INVALID"));
    }

    @Test
    void missingReasonOnRecordAndVoidReturnsReasonInvalid() throws Exception {
        String token = token("ADMINISTRATIVO");
        String withoutReason = "{\"subjectId\":" + subject.getId() + ",\"originInstitution\":\"U\",\"originSubjectName\":\"M\","
                + "\"recognizedOn\":\"" + LocalDate.now() + "\",\"resolutionNumber\":\"R\"}";
        record(token, withoutReason).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EQUIVALENCE_REASON_INVALID"));
        record(token, body(subject.getId(), null, LocalDate.now(), "          ")).andExpect(status().isUnprocessableEntity());
        record(token, body(subject.getId(), null, LocalDate.now(), REASON)).andExpect(status().isCreated());
        Long id = equivalenceRepository.findByProgramEnrollmentIdOrderByRecognizedOnDescIdDesc(enrollment.getId()).get(0).getId();
        mockMvc.perform(post("/program-enrollments/{id}/equivalences/{eid}/void", enrollment.getId(), id)
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EQUIVALENCE_REASON_INVALID"));
    }

    @Test
    void voidIsAllowedEvenWhenEnrollmentIsNoLongerEnrolled() throws Exception {
        String token = token("ADMINISTRATIVO");
        record(token, body(subject.getId(), null, LocalDate.now(), REASON)).andExpect(status().isCreated());
        Long id = equivalenceRepository.findByProgramEnrollmentIdOrderByRecognizedOnDescIdDesc(enrollment.getId()).get(0).getId();
        enrollment.setStatus(ProgramEnrollmentStatus.COMPLETED);
        entityManager.flush();
        voidEquivalence(token, id, REASON).andExpect(status().isOk());
    }

    @Test
    void documentOfAnotherStudentIsRejected() throws Exception {
        record(token("ADMINISTRATIVO"), bodyWithDocument(subject.getId(), 999999L))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EQUIVALENCE_DOCUMENT_INVALID"));
    }

    @Test
    void voidingKeepsRecordStoresReasonAuditsAndPublishesEvent() throws Exception {
        String token = token("ADMINISTRATIVO");
        record(token, body(subject.getId(), "9", LocalDate.now(), REASON)).andExpect(status().isCreated());
        SubjectEquivalence saved = equivalenceRepository.findByProgramEnrollmentIdOrderByRecognizedOnDescIdDesc(enrollment.getId()).get(0);

        voidEquivalence(token, saved.getId(), "Se cargó sobre la materia equivocada")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VOIDED"))
                .andExpect(jsonPath("$.voidReason").value("Se cargó sobre la materia equivocada"))
                .andExpect(jsonPath("$.voidedBy.personId").isNumber());

        entityManager.clear();
        SubjectEquivalence voided = equivalenceRepository.findById(saved.getId()).orElseThrow();
        assertThat(voided.getStatus()).isEqualTo(SubjectEquivalenceStatus.VOIDED);
        assertThat(voided.getVoidedAt()).isNotNull();
        assertThat(jdbc.queryForObject("select count(*) from audit_log where action = ? and entity_id = ? and reason = ?",
                Integer.class, AuditAction.SUBJECT_EQUIVALENCE_VOIDED.name(), saved.getId(),
                "Se cargó sobre la materia equivocada")).isEqualTo(1);
        assertThat(events.stream(SubjectEquivalenceVoidedEvent.class).count()).isEqualTo(1);
    }

    @Test
    void voidingTwiceReturnsEquivalenceAlreadyVoided() throws Exception {
        String token = token("DIRECTOR");
        record(token, body(subject.getId(), null, LocalDate.now(), REASON)).andExpect(status().isCreated());
        Long id = equivalenceRepository.findByProgramEnrollmentIdOrderByRecognizedOnDescIdDesc(enrollment.getId()).get(0).getId();
        voidEquivalence(token, id, REASON).andExpect(status().isOk());
        voidEquivalence(token, id, REASON)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EQUIVALENCE_ALREADY_VOIDED"));
    }

    @Test
    void voidingRequiresReasonOfTenCharacters() throws Exception {
        String token = token("ADMINISTRATIVO");
        record(token, body(subject.getId(), null, LocalDate.now(), REASON)).andExpect(status().isCreated());
        Long id = equivalenceRepository.findByProgramEnrollmentIdOrderByRecognizedOnDescIdDesc(enrollment.getId()).get(0).getId();
        voidEquivalence(token, id, "corto")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EQUIVALENCE_REASON_INVALID"));
        voidEquivalence(token, id, "0123456789").andExpect(status().isOk());
    }

    @Test
    void preceptorCannotVoid() throws Exception {
        record(token("ADMINISTRATIVO"), body(subject.getId(), null, LocalDate.now(), REASON)).andExpect(status().isCreated());
        Long id = equivalenceRepository.findByProgramEnrollmentIdOrderByRecognizedOnDescIdDesc(enrollment.getId()).get(0).getId();
        voidEquivalence(token("PRECEPTOR"), id, REASON).andExpect(status().isForbidden());
    }

    @Test
    void voidingUnknownEquivalenceOrFromAnotherEnrollmentReturns404() throws Exception {
        String token = token("ADMINISTRATIVO");
        voidEquivalence(token, 987654321L, REASON).andExpect(status().isNotFound());
        ProgramEnrollment otherEnrollment = persistEnrollment(student, program, ProgramEnrollmentStatus.COMPLETED);
        entityManager.flush();
        record(token, body(subject.getId(), null, LocalDate.now(), REASON)).andExpect(status().isCreated());
        Long id = equivalenceRepository.findByProgramEnrollmentIdOrderByRecognizedOnDescIdDesc(enrollment.getId()).get(0).getId();
        mockMvc.perform(post("/program-enrollments/{id}/equivalences/{eid}/void", otherEnrollment.getId(), id)
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"" + REASON + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void sameSubjectCanBeRecordedAgainAfterVoiding() throws Exception {
        String token = token("ADMINISTRATIVO");
        record(token, body(subject.getId(), "6", LocalDate.now(), REASON)).andExpect(status().isCreated());
        Long firstId = equivalenceRepository.findByProgramEnrollmentIdOrderByRecognizedOnDescIdDesc(enrollment.getId()).get(0).getId();
        voidEquivalence(token, firstId, REASON).andExpect(status().isOk());

        record(token, body(subject.getId(), "7", LocalDate.now(), REASON)).andExpect(status().isCreated());

        list(token, "ALL").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        list(token, "ACTIVE").andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].originScore").value(7));
        list(token, "VOIDED").andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(firstId));
    }

    @Test
    void listDefaultsToActiveOnly() throws Exception {
        String token = token("ADMINISTRATIVO");
        record(token, body(subject.getId(), null, LocalDate.now(), REASON)).andExpect(status().isCreated());
        Long id = equivalenceRepository.findByProgramEnrollmentIdOrderByRecognizedOnDescIdDesc(enrollment.getId()).get(0).getId();
        voidEquivalence(token, id, REASON).andExpect(status().isOk());

        mockMvc.perform(get("/program-enrollments/{id}/equivalences", enrollment.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void subjectLeavingThePlanKeepsRowWithInCurrentPlanFalse() throws Exception {
        String token = token("ADMINISTRATIVO");
        record(token, body(subject.getId(), null, LocalDate.now(), REASON)).andExpect(status().isCreated());
        jdbc.update("update program_subject set status = 'INACTIVE' where program_id = ? and subject_id = ?", program.getId(), subject.getId());
        entityManager.clear();

        list(token, "ACTIVE")
                .andExpect(jsonPath("$[0].inCurrentPlan").value(false))
                .andExpect(jsonPath("$[0].credits").doesNotExist());
    }

    @Test
    void ownerSeesOnlyActiveWithoutInternalReasonAndOthersGet404() throws Exception {
        String staff = token("ADMINISTRATIVO");
        Subject other = persistSubjectInPlan(program, "Materia B");
        entityManager.flush();
        record(staff, body(subject.getId(), "8", LocalDate.now(), REASON)).andExpect(status().isCreated());
        record(staff, body(other.getId(), null, LocalDate.now(), REASON)).andExpect(status().isCreated());
        Long voidedId = equivalenceRepository.findByProgramEnrollmentIdAndSubjectIdAndStatus(
                enrollment.getId(), other.getId(), SubjectEquivalenceStatus.ACTIVE).orElseThrow().getId();
        voidEquivalence(staff, voidedId, REASON).andExpect(status().isOk());

        mockMvc.perform(get("/me/program-enrollments/{id}/equivalences", enrollment.getId())
                        .header("Authorization", "Bearer " + tokenForStudent(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].subjectId").value(subject.getId()))
                .andExpect(jsonPath("$[0].reason").doesNotExist())
                .andExpect(jsonPath("$[0].recordedBy").doesNotExist());

        Institution institution = new Institution();
        institution.setName("Otra");
        entityManager.persist(institution);
        Student stranger = persistStudent("61000002", institution);
        mockMvc.perform(get("/me/program-enrollments/{id}/equivalences", enrollment.getId())
                        .header("Authorization", "Bearer " + tokenForStudent(stranger)))
                .andExpect(status().isNotFound());
    }

    @Test
    void unknownEnrollmentReturns404() throws Exception {
        mockMvc.perform(get("/program-enrollments/{id}/equivalences", 987654321L)
                        .header("Authorization", "Bearer " + token("PRECEPTOR")))
                .andExpect(status().isNotFound());
    }

    private ResultActions record(String token, String body) throws Exception {
        return mockMvc.perform(post("/program-enrollments/{id}/equivalences", enrollment.getId())
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions voidEquivalence(String token, Long equivalenceId, String reason) throws Exception {
        return mockMvc.perform(post("/program-enrollments/{id}/equivalences/{eid}/void", enrollment.getId(), equivalenceId)
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"" + reason + "\"}"));
    }

    private ResultActions list(String token, String status) throws Exception {
        return mockMvc.perform(get("/program-enrollments/{id}/equivalences", enrollment.getId())
                .param("status", status).header("Authorization", "Bearer " + token));
    }

    private String body(Long subjectId, String score, LocalDate recognizedOn, String reason) {
        String scoreField = score == null ? "" : "\"originScore\":" + score + ",";
        return "{\"subjectId\":" + subjectId + ",\"originInstitution\":\"Universidad de origen\","
                + "\"originSubjectName\":\"Materia de origen\"," + scoreField + "\"recognizedOn\":\"" + recognizedOn
                + "\",\"resolutionNumber\":\"RES-2026-1\",\"reason\":\"" + reason + "\"}";
    }

    private String bodyWithDocument(Long subjectId, Long documentId) {
        return "{\"subjectId\":" + subjectId + ",\"originInstitution\":\"Universidad de origen\","
                + "\"originSubjectName\":\"Materia de origen\",\"recognizedOn\":\"" + LocalDate.now()
                + "\",\"resolutionNumber\":\"RES-2026-1\",\"studentDocumentId\":" + documentId + ",\"reason\":\"" + REASON + "\"}";
    }

    private String token(String roleName) {
        staffCounter++;
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        String username = "equiv-" + roleName.toLowerCase() + "-" + staffCounter + "-" + System.nanoTime();
        person.setUsername(username);
        person.setDni(Long.toString(DNI_SEQUENCE.incrementAndGet()));
        person.setName("Persona");
        person.setLastname("Prueba");
        person.setEmail(username + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        Role role = roleRepository.findByName(roleName).orElseThrow();
        person.getRoles().add(role);
        entityManager.persist(person);
        entityManager.flush();
        return jwtService.generateAccessToken(person);
    }

    private String tokenForStudent(Student target) {
        Role role = roleRepository.findByName("ALUMNO").orElseThrow();
        target.getPerson().getRoles().add(role);
        entityManager.persist(target.getPerson());
        entityManager.flush();
        return jwtService.generateAccessToken(target.getPerson());
    }

    private Student persistStudent(String dni, Institution institution) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setName("Alumno");
        person.setLastname(dni);
        person.setDni(dni);
        person.setUsername(dni);
        person.setEmail(dni + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        entityManager.persist(person);
        Student created = new Student();
        created.setPerson(person);
        created.setInstitution(institution);
        created.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(created);
        return created;
    }

    private Subject persistSubject(String name) {
        Subject created = new Subject();
        created.setCode(TestSubjectCodes.next());
        created.setName(name);
        entityManager.persist(created);
        return created;
    }

    private Subject persistSubjectInPlan(Program target, String name) {
        Subject created = persistSubject(name);
        ProgramSubject planEntry = new ProgramSubject();
        planEntry.setProgram(target);
        planEntry.setSubject(created);
        planEntry.setCredits(5);
        planEntry.setCurricularYear(1);
        planEntry.setCurricularTerm(CurricularTerm.FIRST);
        planEntry.setStatus(StatusType.ACTIVE);
        entityManager.persist(planEntry);
        return created;
    }

    private ProgramEnrollment persistEnrollment(Student owner, Program target, ProgramEnrollmentStatus status) {
        ProgramEnrollment created = new ProgramEnrollment();
        created.setStudent(owner);
        created.setProgram(target);
        created.setStatus(status);
        created.setEnrollmentDate(LocalDateTime.now());
        entityManager.persist(created);
        return created;
    }

    private void persistSectionEnrollment(Subject target, EnrollmentStatus status) {
        Term term = TermFixtures.newTerm();
        term.setYear(2026);
        term.setDescription("2026-1C");
        term.setStatus(StatusType.ACTIVE);
        entityManager.persist(term);
        SubjectSection section = new SubjectSection();
        section.setName(target.getName() + " - Com A");
        section.setSubject(target);
        section.setTerm(term);
        section.setCapacity(30);
        section.setStatus(StatusType.ACTIVE);
        entityManager.persist(section);
        SubjectSectionEnrollment sectionEnrollment = new SubjectSectionEnrollment();
        sectionEnrollment.setStudent(student);
        sectionEnrollment.setProgramEnrollment(enrollment);
        sectionEnrollment.setSubjectSection(section);
        sectionEnrollment.setStatus(status);
        sectionEnrollment.setEnrolledAt(LocalDateTime.now());
        sectionEnrollment.setAttemptNumber(1);
        entityManager.persist(sectionEnrollment);
        entityManager.flush();
    }
}
