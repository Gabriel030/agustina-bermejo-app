package com.red_escuela.backend.graduation.debtexception;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectEquivalence;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.evaluation.entity.ExamBoardInstructor;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistration;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistrationStatus;
import com.red_escuela.backend.evaluation.entity.ExamBoardStatus;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.platform.infrastructure.FeatureSnapshotHolder;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.Grade;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.time.MutableClock;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class DebtExceptionIssuanceIT extends AbstractIntegrationTest {

    private static final String REASON = "Convenio de pago firmado con la familia del alumno";
    private static final Map<String, String> DOCUMENT_TABLES = Map.of(
            "REGULAR_STUDENT_CERTIFICATE", "regular_student_certificate",
            "EXAM_REGISTRATION_CERTIFICATE", "exam_registration_certificate",
            "EXAM_CERTIFICATE", "exam_certificate",
            "APPROVED_SUBJECTS_CERTIFICATE", "approved_subjects_certificate");

    @Autowired
    private TransactionTemplate transactions;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private FeatureSnapshotHolder snapshotHolder;

    private Long studentId;
    private Long strangerStudentId;
    private Long enrollmentId;
    private Long upcomingExamRegistrationId;
    private Long heldExamRegistrationId;
    private String administrativeToken;

    @BeforeEach
    void seed() {
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 11, 10, 10, 0));
        deleteDocuments();
        transactions.executeWithoutResult(status -> {
            CycleTestFixtures fixtures = new CycleTestFixtures(entityManager);
            Institution institution = new Institution();
            institution.setName("Instituto de prueba");
            entityManager.persist(institution);
            Person studentPerson = persistPerson("ALUMNO");
            Student student = persistStudent(studentPerson, institution);
            Student stranger = persistStudent(persistPerson("ALUMNO"), institution);
            Program program = fixtures.program();
            ProgramEnrollment enrollment = new ProgramEnrollment();
            enrollment.setStudent(student);
            enrollment.setProgram(program);
            enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
            enrollment.setEnrollmentDate(LocalDateTime.of(2026, 3, 1, 10, 0));
            entityManager.persist(enrollment);
            Term term = fixtures.term(2026);
            Subject upcomingSubject = fixtures.subject();
            SubjectSection upcomingSection = fixtures.section(upcomingSubject, term, StatusType.ACTIVE, null);
            upcomingSection.setMaxAbsenceRate(Percentage.ofPoints(new BigDecimal("25")));
            SubjectSectionEnrollment upcomingEnrollment = persistSectionEnrollment(student, enrollment, upcomingSection);
            Subject heldSubject = fixtures.subject();
            SubjectSection heldSection = fixtures.section(heldSubject, term, StatusType.ACTIVE, null);
            SubjectSectionEnrollment heldEnrollment = persistSectionEnrollment(student, enrollment, heldSection);
            Instructor instructor = fixtures.instructor();
            recordAttendance(upcomingEnrollment, instructor);
            ExamBoardRegistration upcoming = persistRegistration(upcomingSubject, term, ExamBoardStatus.SCHEDULED,
                    LocalDateTime.of(2026, 11, 20, 9, 0), upcomingEnrollment, ExamBoardRegistrationStatus.REGISTERED, instructor);
            ExamBoardRegistration held = persistRegistration(heldSubject, term, ExamBoardStatus.CLOSED,
                    LocalDateTime.of(2026, 11, 5, 9, 0), heldEnrollment, ExamBoardRegistrationStatus.GRADED, instructor);
            Subject approvedSubject = fixtures.subject();
            ProgramSubject entry = new ProgramSubject();
            entry.setProgram(program);
            entry.setSubject(approvedSubject);
            entry.setCredits(4);
            entry.setStatus(StatusType.ACTIVE);
            entityManager.persist(entry);
            SubjectEquivalence equivalence = new SubjectEquivalence();
            equivalence.setProgramEnrollment(enrollment);
            equivalence.setSubjectId(approvedSubject.getId());
            equivalence.setOriginInstitution("Otra institución");
            equivalence.setOriginSubjectName("Materia de origen");
            equivalence.setOriginScore(Grade.of(new BigDecimal("8")));
            equivalence.setRecognizedOn(LocalDate.of(2026, 5, 20));
            equivalence.setResolutionNumber("R-1");
            equivalence.setReason("Equivalencia de prueba");
            equivalence.setRecordedById(studentPerson.getId());
            equivalence.setRecordedAt(LocalDateTime.of(2026, 5, 20, 10, 0));
            entityManager.persist(equivalence);
            entityManager.flush();
            BillingTestFixtures.invoice().forProgramEnrollment(enrollment).ofType(FeeType.MONTHLY_INSTALLMENT)
                    .forPeriod(YearMonth.of(2026, 3)).dueOn(LocalDate.of(2026, 3, 10)).withBaseAmount(ars(15000)).build();
            studentId = student.getId();
            strangerStudentId = stranger.getId();
            enrollmentId = enrollment.getId();
            upcomingExamRegistrationId = upcoming.getId();
            heldExamRegistrationId = held.getId();
            administrativeToken = jwtService.generateAccessToken(persistPerson("ADMINISTRATIVO"));
        });
        setBillingMode("FULL");
    }

    @AfterEach
    void cleanUp() {
        deleteDocuments();
        jdbc.update("delete from document_debt_exception");
        MutableClock.INSTANCE.reset();
        setBillingMode("FULL");
    }

    @Test
    void regularCertificateIsIssuedByDebtException() throws Exception {
        assertIssuedByException("REGULAR_STUDENT_CERTIFICATE", enrollmentId, null);
    }

    @Test
    void examRegistrationCertificateIsIssuedByDebtException() throws Exception {
        assertIssuedByException("EXAM_REGISTRATION_CERTIFICATE", null, upcomingExamRegistrationId);
    }

    @Test
    void examCertificateIsIssuedByDebtException() throws Exception {
        assertIssuedByException("EXAM_CERTIFICATE", null, heldExamRegistrationId);
    }

    @Test
    void approvedSubjectsCertificateIsIssuedByDebtException() throws Exception {
        assertIssuedByException("APPROVED_SUBJECTS_CERTIFICATE", enrollmentId, null);
    }

    @Test
    void regularCertificateRejectsAScopeOfAnotherStudent() throws Exception {
        assertForeignScopeIsRejected("REGULAR_STUDENT_CERTIFICATE", enrollmentId, null);
    }

    @Test
    void examRegistrationCertificateRejectsAScopeOfAnotherStudent() throws Exception {
        assertForeignScopeIsRejected("EXAM_REGISTRATION_CERTIFICATE", null, upcomingExamRegistrationId);
    }

    @Test
    void examCertificateRejectsAScopeOfAnotherStudent() throws Exception {
        assertForeignScopeIsRejected("EXAM_CERTIFICATE", null, heldExamRegistrationId);
    }

    @Test
    void approvedSubjectsCertificateRejectsAScopeOfAnotherStudent() throws Exception {
        assertForeignScopeIsRejected("APPROVED_SUBJECTS_CERTIFICATE", enrollmentId, null);
    }

    @Test
    void everyTypeRejectsAnExceptionWithoutItsScope() throws Exception {
        for (String type : DOCUMENT_TABLES.keySet()) {
            issue(studentId, type, null, null).andExpect(status().isNotFound());
        }
        assertThat(jdbc.queryForObject("select count(*) from document_debt_exception", Integer.class)).isZero();
    }

    private void assertIssuedByException(String documentType, Long enrollment, Long registration) throws Exception {
        issue(studentId, documentType, enrollment, registration).andExpect(status().isCreated());
        Long exceptionId = jdbc.queryForObject("select max(id) from document_debt_exception", Long.class);

        MvcResult first = download(exceptionId).andExpect(status().isOk()).andReturn();

        assertThat(first.getResponse().getContentAsByteArray()).startsWith("%PDF".getBytes());
        assertThat(documentCount(documentType)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from verifiable_document where document_type = ?", Integer.class, documentType))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from document_debt_exception where id = ?", String.class, exceptionId))
                .isEqualTo("USED");
        assertThat(auditCount("DOCUMENT_DEBT_EXC_USED", exceptionId)).isEqualTo(1);
        assertThat(auditCount("DOCUMENT_DEBT_EXC_DOWNLOADED", exceptionId)).isEqualTo(1);

        download(exceptionId).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DEBT_EXCEPTION_ALREADY_USED"));
        assertThat(documentCount(documentType)).isEqualTo(1);
        assertThat(auditCount("DOCUMENT_DEBT_EXC_DOWNLOADED", exceptionId)).isEqualTo(1);
    }

    private void assertForeignScopeIsRejected(String documentType, Long enrollment, Long registration) throws Exception {
        issue(strangerStudentId, documentType, enrollment, registration).andExpect(status().isNotFound());

        assertThat(jdbc.queryForObject("select count(*) from document_debt_exception", Integer.class)).isZero();
        assertThat(documentCount(documentType)).isZero();
    }

    private int documentCount(String documentType) {
        return jdbc.queryForObject("select count(*) from " + DOCUMENT_TABLES.get(documentType), Integer.class);
    }

    private int auditCount(String action, Long entityId) {
        return jdbc.queryForObject("select count(*) from audit_log where action = ? and entity_id = ?", Integer.class, action, entityId);
    }

    private ResultActions issue(Long student, String documentType, Long enrollment, Long registration) throws Exception {
        return mockMvc.perform(post("/document-debt-exceptions").header("Authorization", "Bearer " + administrativeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"studentId\":" + student + ",\"documentType\":\"" + documentType + "\","
                        + (enrollment == null ? "" : "\"enrollmentId\":" + enrollment + ",")
                        + (registration == null ? "" : "\"registrationId\":" + registration + ",")
                        + "\"reason\":\"" + REASON + "\",\"validityDays\":5}"));
    }

    private ResultActions download(Long exceptionId) throws Exception {
        return mockMvc.perform(get("/document-debt-exceptions/" + exceptionId + "/pdf")
                .header("Authorization", "Bearer " + administrativeToken));
    }

    private void deleteDocuments() {
        jdbc.update("delete from verifiable_document where document_type in ('REGULAR_STUDENT_CERTIFICATE', "
                + "'EXAM_REGISTRATION_CERTIFICATE', 'EXAM_CERTIFICATE', 'APPROVED_SUBJECTS_CERTIFICATE')");
        jdbc.update("update regular_certificate_request set certificate_id = null");
        DOCUMENT_TABLES.values().forEach(table -> jdbc.update("delete from " + table));
        jdbc.update("delete from regular_certificate_request");
    }

    private void setBillingMode(String mode) {
        jdbc.update("update platform_feature set enabled = ?, mode = ? where code = 'BILLING'", !"OFF".equals(mode), mode);
        snapshotHolder.refresh();
    }

    private SubjectSectionEnrollment persistSectionEnrollment(Student student, ProgramEnrollment enrollment, SubjectSection section) {
        SubjectSectionEnrollment sectionEnrollment = new SubjectSectionEnrollment();
        sectionEnrollment.setStudent(student);
        sectionEnrollment.setProgramEnrollment(enrollment);
        sectionEnrollment.setSubjectSection(section);
        sectionEnrollment.setStatus(EnrollmentStatus.REGULAR);
        sectionEnrollment.setEnrolledAt(LocalDateTime.of(2026, 3, 1, 10, 0));
        sectionEnrollment.setAttemptNumber(1);
        entityManager.persist(sectionEnrollment);
        return sectionEnrollment;
    }

    private void recordAttendance(SubjectSectionEnrollment sectionEnrollment, Instructor instructor) {
        for (int day = 0; day < 9; day++) {
            Attendance attendance = new Attendance();
            attendance.setProgramSubjectSectionEnrollment(sectionEnrollment);
            attendance.setClassDate(LocalDate.of(2026, 4, 1).plusDays(day));
            attendance.setStatus(day < 8 ? AttendanceStatus.PRESENT : AttendanceStatus.ABSENT);
            attendance.setRecordedBy(instructor);
            entityManager.persist(attendance);
        }
    }

    private ExamBoardRegistration persistRegistration(Subject subject, Term term, ExamBoardStatus boardStatus, LocalDateTime examDate,
                                                      SubjectSectionEnrollment sectionEnrollment, ExamBoardRegistrationStatus status,
                                                      Instructor tribunalMember) {
        ExamBoard board = new ExamBoard();
        board.setSubject(subject);
        board.setTerm(term);
        board.setExamDate(examDate);
        board.setStatus(boardStatus);
        entityManager.persist(board);
        ExamBoardInstructor member = new ExamBoardInstructor();
        member.setExamBoard(board);
        member.setInstructor(tribunalMember);
        entityManager.persist(member);
        ExamBoardRegistration registration = new ExamBoardRegistration();
        registration.setExamBoard(board);
        registration.setProgramSubjectSectionEnrollment(sectionEnrollment);
        registration.setStatus(status);
        registration.setRegisteredAt(LocalDateTime.of(2026, 10, 20, 9, 0));
        entityManager.persist(registration);
        return registration;
    }

    private Student persistStudent(Person person, Institution institution) {
        Student student = new Student();
        student.setPerson(person);
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        return student;
    }

    private Person persistPerson(String roleName) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        String suffix = UUID.randomUUID().toString();
        person.setName("Persona");
        person.setLastname(suffix);
        person.setDni(suffix.substring(0, 12));
        person.setUsername("debtissue." + suffix);
        person.setEmail("debtissue." + suffix + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        person.getRoles().add(roleRepository.findByName(roleName).orElseThrow());
        entityManager.persist(person);
        entityManager.flush();
        return person;
    }
}
