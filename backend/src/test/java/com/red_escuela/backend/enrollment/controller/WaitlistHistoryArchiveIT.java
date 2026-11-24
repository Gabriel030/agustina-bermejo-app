package com.red_escuela.backend.enrollment.controller;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycle;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.config.AbstractIntegrationTest;
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
import com.red_escuela.backend.shared.audit.AuditLogRepository;
import com.red_escuela.backend.shared.audit.AuditRecord;
import com.red_escuela.backend.shared.audit.service.AuditLogArchiveJob;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.retention.RetentionSettings;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class WaitlistHistoryArchiveIT extends AbstractIntegrationTest {

    private static final String WITHDRAWN_BY_STAFF = AuditAction.ENROLLMENT_WITHDRAWN_BY_STAFF.name();

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
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private Clock clock;

    @Test
    void entryMovedToArchiveByTheJobStaysInTheHistoryNewestFirst() throws Exception {
        LocalDateTime now = LocalDateTime.now(clock);
        Program program = new CycleTestFixtures(entityManager).program();
        SubjectSection section = queueSection(program, 2049);
        SubjectSectionEnrollment waiting = queuedEnrollment(program, section, now);
        auditTrail.record(new AuditRecord(AuditAction.ENROLLMENT_WITHDRAWN_BY_STAFF, AuditEntityType.SECTION_ENROLLMENT,
                waiting.getId(), "ENROLLED", "WITHDRAWN", "Reciente"));
        long oldEntryId = insertAuditEntry("audit_log", now.minusMonths(13), waiting.getId(), "Movida por el job");
        entityManager.flush();
        archiveJob().archiveExpiredAuditEntries();
        assertThat(archivedCount(oldEntryId)).isEqualTo(1L);

        mockMvc.perform(get("/waitlists/{kind}/{id}/history", "PROGRAM_SECTION", section.getId())
                        .header("Authorization", "Bearer " + tokenWithRole("preceptor-arch-a", "PRECEPTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].reason").value("Reciente"))
                .andExpect(jsonPath("$.items[1].reason").value("Movida por el job"))
                .andExpect(jsonPath("$.items[1].action").value(WITHDRAWN_BY_STAFF))
                .andExpect(jsonPath("$.items[1].studentName").isNotEmpty());
    }

    @Test
    void pagesCountLiveAndArchivedEntriesTogetherWithoutRepeatingThem() throws Exception {
        LocalDateTime now = LocalDateTime.now(clock);
        Program program = new CycleTestFixtures(entityManager).program();
        SubjectSection section = queueSection(program, 2050);
        SubjectSectionEnrollment waiting = queuedEnrollment(program, section, now);
        insertAuditEntry("audit_log", now, waiting.getId(), "A-viva");
        insertAuditEntry("audit_log_archive", now.minusMonths(1), waiting.getId(), "C-archivo-directo");
        insertAuditEntry("audit_log", now.minusMonths(2), waiting.getId(), "D-viva");
        long oldEntryId = insertAuditEntry("audit_log", now.minusMonths(13), waiting.getId(), "B-movida-por-job");
        entityManager.flush();
        archiveJob().archiveExpiredAuditEntries();
        assertThat(archivedCount(oldEntryId)).isEqualTo(1L);

        String token = tokenWithRole("preceptor-arch-b", "PRECEPTOR");
        mockMvc.perform(get("/waitlists/{kind}/{id}/history", "PROGRAM_SECTION", section.getId())
                        .param("page", "0").param("size", "2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.items[0].reason").value("A-viva"))
                .andExpect(jsonPath("$.items[1].reason").value("C-archivo-directo"));
        mockMvc.perform(get("/waitlists/{kind}/{id}/history", "PROGRAM_SECTION", section.getId())
                        .param("page", "1").param("size", "2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].reason").value("D-viva"))
                .andExpect(jsonPath("$.items[1].reason").value("B-movida-por-job"));
        mockMvc.perform(get("/waitlists/{kind}/{id}/history", "PROGRAM_SECTION", section.getId())
                        .param("size", "100")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.items.length()").value(4));
    }

    private long archivedCount(long id) {
        return jdbc.queryForObject("select count(*) from audit_log_archive where id = ?", Long.class, id);
    }

    private AuditLogArchiveJob archiveJob() {
        return new AuditLogArchiveJob(auditLogRepository,
                new RetentionSettings(true, 12, 12, 12, 36, 100_000, 1), clock, transactionManager, 12);
    }

    private long insertAuditEntry(String table, LocalDateTime occurredAt, Long enrollmentId, String reason) {
        return jdbc.queryForObject("insert into " + table + " (occurred_at, action, entity_type, entity_id, reason) "
                + "values (?, ?, ?, ?, ?) returning id", Long.class, occurredAt, WITHDRAWN_BY_STAFF,
                AuditEntityType.SECTION_ENROLLMENT.name(), enrollmentId, reason);
    }

    private SubjectSection queueSection(Program program, int termYear) {
        CycleTestFixtures fixtures = new CycleTestFixtures(entityManager);
        Term term = fixtures.term(termYear);
        Subject subject = fixtures.subject();
        AcademicOfferingCycle cycle = fixtures.programCycle(program, term, AcademicOfferingCycleStatus.APPROVED);
        return fixtures.section(subject, term, StatusType.ACTIVE, cycle);
    }

    private SubjectSectionEnrollment queuedEnrollment(Program program, SubjectSection section, LocalDateTime enrolledAt) {
        CycleTestFixtures fixtures = new CycleTestFixtures(entityManager);
        Institution institution = new Institution();
        institution.setName("Instituto");
        entityManager.persist(institution);
        Student student = new Student();
        student.setPerson(fixtures.person());
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        ProgramEnrollment programEnrollment = new ProgramEnrollment();
        programEnrollment.setStudent(student);
        programEnrollment.setProgram(program);
        programEnrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        programEnrollment.setEnrollmentDate(enrolledAt);
        entityManager.persist(programEnrollment);
        SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
        enrollment.setProgramEnrollment(programEnrollment);
        enrollment.setStudent(student);
        enrollment.setSubjectSection(section);
        enrollment.setStatus(EnrollmentStatus.WAITLISTED);
        enrollment.setEnrolledAt(enrolledAt);
        enrollment.setAttemptNumber(1);
        entityManager.persist(enrollment);
        entityManager.flush();
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
