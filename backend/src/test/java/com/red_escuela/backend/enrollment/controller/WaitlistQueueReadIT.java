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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class WaitlistQueueReadIT extends AbstractIntegrationTest {

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2043, 2, 3, 9, 0);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private AuditTrail auditTrail;

    @Test
    void queueIsOrderedByJoinTimeThenIdAndFlagsEachVerdict() throws Exception {
        CycleTestFixtures fixtures = new CycleTestFixtures(entityManager);
        Program program = fixtures.program();
        Term term = fixtures.term(2043);
        Subject subject = fixtures.subject();
        AcademicOfferingCycle cycle = fixtures.programCycle(program, term, AcademicOfferingCycleStatus.APPROVED);
        SubjectSection section = fixtures.section(subject, term, StatusType.ACTIVE, cycle);
        SubjectSection otherSection = fixtures.section(subject, term, StatusType.ACTIVE, cycle);
        section.setCapacity(2);
        Institution institution = institution();
        persistEnrollment(fixtures, institution, program, section, EnrollmentStatus.ENROLLED, BASE_TIME, null);
        persistEnrollment(fixtures, institution, program, section, EnrollmentStatus.IN_PROGRESS, BASE_TIME, null);
        SubjectSectionEnrollment joinedLast = persistEnrollment(fixtures, institution, program, section,
                EnrollmentStatus.WAITLISTED, BASE_TIME.plusHours(5), null);
        SubjectSectionEnrollment joinedFirst = persistEnrollment(fixtures, institution, program, section,
                EnrollmentStatus.WAITLISTED, BASE_TIME.plusHours(1), null);
        SubjectSectionEnrollment tiedLowerId = persistEnrollment(fixtures, institution, program, section,
                EnrollmentStatus.WAITLISTED, BASE_TIME.plusHours(2), null);
        SubjectSectionEnrollment tiedHigherId = persistEnrollment(fixtures, institution, program, section,
                EnrollmentStatus.WAITLISTED, BASE_TIME.plusHours(2), otherSection);
        entityManager.flush();

        mockMvc.perform(get("/waitlists/{kind}/{id}", "PROGRAM_SECTION", section.getId())
                        .header("Authorization", "Bearer " + tokenWithRole("preceptor-wl", "PRECEPTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("PROGRAM_SECTION"))
                .andExpect(jsonPath("$.capacity").value(2))
                .andExpect(jsonPath("$.occupied").value(2))
                .andExpect(jsonPath("$.free").value(0))
                .andExpect(jsonPath("$.waiting").value(4))
                .andExpect(jsonPath("$.entries.length()").value(4))
                .andExpect(jsonPath("$.entries[0].position").value(1))
                .andExpect(jsonPath("$.entries[0].enrollmentId").value(joinedFirst.getId()))
                .andExpect(jsonPath("$.entries[0].verdict.eligible").value(true))
                .andExpect(jsonPath("$.entries[1].enrollmentId").value(tiedLowerId.getId()))
                .andExpect(jsonPath("$.entries[2].enrollmentId").value(tiedHigherId.getId()))
                .andExpect(jsonPath("$.entries[2].verdict.eligible").value(false))
                .andExpect(jsonPath("$.entries[2].verdict.reason").value("ALREADY_PASSED"))
                .andExpect(jsonPath("$.entries[3].enrollmentId").value(joinedLast.getId()))
                .andExpect(jsonPath("$.entries[0].student.documentNumber").isNotEmpty());
    }

    @Test
    void listCountsOnlyQueuesWithPeopleWaitingOrderedByWaiting() throws Exception {
        CycleTestFixtures fixtures = new CycleTestFixtures(entityManager);
        Program program = fixtures.program();
        Term term = fixtures.term(2044);
        Subject subject = fixtures.subject();
        AcademicOfferingCycle cycle = fixtures.programCycle(program, term, AcademicOfferingCycleStatus.APPROVED);
        SubjectSection busy = fixtures.section(subject, term, StatusType.ACTIVE, cycle);
        SubjectSection quiet = fixtures.section(subject, term, StatusType.ACTIVE, cycle);
        SubjectSection withoutQueue = fixtures.section(subject, term, StatusType.ACTIVE, cycle);
        Institution institution = institution();
        persistEnrollment(fixtures, institution, program, busy, EnrollmentStatus.WAITLISTED, BASE_TIME, null);
        persistEnrollment(fixtures, institution, program, busy, EnrollmentStatus.WAITLISTED, BASE_TIME, null);
        persistEnrollment(fixtures, institution, program, quiet, EnrollmentStatus.WAITLISTED, BASE_TIME, null);
        persistEnrollment(fixtures, institution, program, withoutQueue, EnrollmentStatus.ENROLLED, BASE_TIME, null);
        entityManager.flush();

        mockMvc.perform(get("/waitlists").param("kind", "PROGRAM_SECTION").param("termId", String.valueOf(term.getId()))
                        .header("Authorization", "Bearer " + tokenWithRole("director-wl", "DIRECTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[0].id").value(busy.getId()))
                .andExpect(jsonPath("$.items[0].waiting").value(2))
                .andExpect(jsonPath("$.items[1].id").value(quiet.getId()))
                .andExpect(jsonPath("$.items[1].waiting").value(1));
    }

    @Test
    void courseQueueExposesTheWaitlistDeadline() throws Exception {
        CycleTestFixtures fixtures = new CycleTestFixtures(entityManager);
        Term term = fixtures.term(2045);
        Course course = new Course();
        course.setName("Cosmetología Facial");
        course.setTerm(term);
        course.setCapacity(1);
        course.setIsActive(StatusType.ACTIVE);
        course.setWaitlistDeadlineOffsetDays(10);
        course.setTeachingMode(TeachingMode.ONLINE);
        entityManager.persist(course);
        Institution institution = institution();
        CourseEnrollment waiting = new CourseEnrollment();
        waiting.setStudent(student(fixtures, institution));
        waiting.setCourse(course);
        waiting.setStatus(EnrollmentStatus.WAITLISTED);
        waiting.setEnrollmentDate(BASE_TIME);
        entityManager.persist(waiting);
        entityManager.flush();

        mockMvc.perform(get("/waitlists/{kind}/{id}", "COURSE", course.getId())
                        .header("Authorization", "Bearer " + tokenWithRole("admin-wl", "ADMINISTRATIVO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cosmetología Facial"))
                .andExpect(jsonPath("$.nextExpiry").value("2045-03-11"))
                .andExpect(jsonPath("$.entries[0].expiresOn").value("2045-03-11"))
                .andExpect(jsonPath("$.entries[0].verdict.eligible").value(false))
                .andExpect(jsonPath("$.entries[0].verdict.reason").value("ADMISSION_RECEIPT_PENDING"));
    }

    @Test
    void historyListsAuditEntriesOfTheQueueEnrollmentsNewestFirst() throws Exception {
        CycleTestFixtures fixtures = new CycleTestFixtures(entityManager);
        Program program = fixtures.program();
        Term term = fixtures.term(2046);
        Subject subject = fixtures.subject();
        AcademicOfferingCycle cycle = fixtures.programCycle(program, term, AcademicOfferingCycleStatus.APPROVED);
        SubjectSection section = fixtures.section(subject, term, StatusType.ACTIVE, cycle);
        SubjectSection unrelated = fixtures.section(subject, term, StatusType.ACTIVE, cycle);
        Institution institution = institution();
        SubjectSectionEnrollment withdrawn = persistEnrollment(fixtures, institution, program, section,
                EnrollmentStatus.WITHDRAWN, BASE_TIME, null);
        SubjectSectionEnrollment elsewhere = persistEnrollment(fixtures, institution, program, unrelated,
                EnrollmentStatus.WITHDRAWN, BASE_TIME, null);
        entityManager.flush();
        auditTrail.record(new AuditRecord(AuditAction.ENROLLMENT_WITHDRAWN_BY_STAFF, AuditEntityType.SECTION_ENROLLMENT,
                withdrawn.getId(), "ENROLLED", "WITHDRAWN", "Pidió la baja"));
        auditTrail.record(new AuditRecord(AuditAction.ENROLLMENT_WITHDRAWN_BY_STAFF, AuditEntityType.SECTION_ENROLLMENT,
                elsewhere.getId(), "ENROLLED", "WITHDRAWN", "Otra comisión"));
        entityManager.flush();

        mockMvc.perform(get("/waitlists/{kind}/{id}/history", "PROGRAM_SECTION", section.getId())
                        .header("Authorization", "Bearer " + tokenWithRole("preceptor-hist", "PRECEPTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].action").value("ENROLLMENT_WITHDRAWN_BY_STAFF"))
                .andExpect(jsonPath("$.items[0].reason").value("Pidió la baja"))
                .andExpect(jsonPath("$.items[0].studentName").isNotEmpty());
    }

    @Test
    void prioritizedEntriesComeFirstWithTheirPrioritizationOrder() throws Exception {
        CycleTestFixtures fixtures = new CycleTestFixtures(entityManager);
        Program program = fixtures.program();
        Term term = fixtures.term(2047);
        Subject subject = fixtures.subject();
        AcademicOfferingCycle cycle = fixtures.programCycle(program, term, AcademicOfferingCycleStatus.APPROVED);
        SubjectSection section = fixtures.section(subject, term, StatusType.ACTIVE, cycle);
        Institution institution = institution();
        SubjectSectionEnrollment older = persistEnrollment(fixtures, institution, program, section,
                EnrollmentStatus.WAITLISTED, BASE_TIME, null);
        SubjectSectionEnrollment prioritized = persistEnrollment(fixtures, institution, program, section,
                EnrollmentStatus.WAITLISTED, BASE_TIME.plusHours(3), null);
        prioritized.setWaitlistPrioritizedAt(BASE_TIME.plusDays(1));
        entityManager.flush();

        mockMvc.perform(get("/waitlists/{kind}/{id}", "PROGRAM_SECTION", section.getId())
                        .header("Authorization", "Bearer " + tokenWithRole("director-wl-prio", "DIRECTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].enrollmentId").value(prioritized.getId()))
                .andExpect(jsonPath("$.entries[0].prioritizationOrder").value(1))
                .andExpect(jsonPath("$.entries[0].prioritizedAt").isNotEmpty())
                .andExpect(jsonPath("$.entries[1].enrollmentId").value(older.getId()))
                .andExpect(jsonPath("$.entries[1].prioritizationOrder").doesNotExist());
    }

    @Test
    void unknownQueueReturns404() throws Exception {
        mockMvc.perform(get("/waitlists/{kind}/{id}", "PROGRAM_SECTION", 999999L)
                        .header("Authorization", "Bearer " + tokenWithRole("preceptor-wl404", "PRECEPTOR")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/waitlists/{kind}/{id}/history", "COURSE", 999999L)
                        .header("Authorization", "Bearer " + tokenWithRole("preceptor-wl404h", "PRECEPTOR")))
                .andExpect(status().isNotFound());
    }

    @Test
    void teacherCannotListWaitlists() throws Exception {
        String teacherToken = tokenWithRole("docente-wl", "DOCENTE");

        mockMvc.perform(get("/waitlists").header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/waitlists/{kind}/{id}", "COURSE", 1L).header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/waitlists/{kind}/{id}/history", "COURSE", 1L).header("Authorization", "Bearer " + teacherToken))
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
