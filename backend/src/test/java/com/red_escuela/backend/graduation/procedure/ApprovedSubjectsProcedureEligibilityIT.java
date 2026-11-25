package com.red_escuela.backend.graduation.procedure;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
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
import com.red_escuela.backend.platform.infrastructure.FeatureSnapshotHolder;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.time.MutableClock;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.UUID;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.red_escuela.backend.enrollment.entity.SubjectEquivalence;
import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.shared.domain.Grade;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.repository.ApprovedSubjectsCertificateRepository;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.specialist.ApprovedSubjectsCertificateIssuer;
@SpringBootTest
@ActiveProfiles("test")
class ApprovedSubjectsProcedureEligibilityIT extends AbstractIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 10, 0);

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
    @Autowired
    private ApprovedSubjectsCertificateIssuer issuer;
    @Autowired
    private ApprovedSubjectsCertificateRepository certificateRepository;

    private Long enrollmentId;
    private Long studentId;
    private Long personId;
    private Long sectionEnrollmentId;
    private int recordedDays;
    private String studentToken;
    private String strangerToken;

    @BeforeEach
    void seed() {
        MutableClock.INSTANCE.setDateTime(NOW);
        jdbc.update("delete from verifiable_document where document_type = 'APPROVED_SUBJECTS_CERTIFICATE'");
        jdbc.update("delete from approved_subjects_certificate");
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
            SubjectSection section = fixtures.section(fixtures.subject(), fixtures.term(2026), StatusType.ACTIVE, null);
            section.setMaxAbsenceRate(Percentage.ofPoints(new BigDecimal("25")));
            SubjectSectionEnrollment sectionEnrollment = new SubjectSectionEnrollment();
            sectionEnrollment.setStudent(student);
            sectionEnrollment.setProgramEnrollment(enrollment);
            sectionEnrollment.setSubjectSection(section);
            sectionEnrollment.setStatus(EnrollmentStatus.ENROLLED);
            sectionEnrollment.setEnrolledAt(LocalDateTime.of(2026, 3, 1, 10, 0));
            entityManager.persist(sectionEnrollment);
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
            enrollmentId = enrollment.getId();
            studentId = student.getId();
            personId = studentPerson.getId();
            sectionEnrollmentId = sectionEnrollment.getId();
            studentToken = jwtService.generateAccessToken(studentPerson);
            strangerToken = jwtService.generateAccessToken(stranger.getPerson());
        });
        recordedDays = 0;
        recordAttendance(8, 1);
        setBillingMode("OFF");
    }

    @AfterEach
    void cleanUp() {
        setBillingMode("FULL");
        jdbc.update("delete from verifiable_document where document_type = 'APPROVED_SUBJECTS_CERTIFICATE'");
        jdbc.update("delete from approved_subjects_certificate");
        MutableClock.INSTANCE.reset();
    }

    private void setBillingMode(String mode) {
        jdbc.update("update platform_feature set enabled = ?, mode = ? where code = 'BILLING'", !"OFF".equals(mode), mode);
        snapshotHolder.refresh();
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
        person.setUsername("approved." + suffix);
        person.setEmail("approved." + suffix + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        Role role = roleRepository.findByName(roleName).orElseThrow();
        person.getRoles().add(role);
        entityManager.persist(person);
        entityManager.flush();
        return person;
    }

    private void recordAttendance(int present, int absent) {
        int attendanceDays = recordedDays;
        recordedDays += present + absent;
        transactions.executeWithoutResult(status -> {
            SubjectSectionEnrollment sectionEnrollment = entityManager.find(SubjectSectionEnrollment.class, sectionEnrollmentId);
            CycleTestFixtures fixtures = new CycleTestFixtures(entityManager);
            var instructor = fixtures.instructor();
            for (int day = 0; day < present + absent; day++) {
                Attendance attendance = new Attendance();
                attendance.setProgramSubjectSectionEnrollment(sectionEnrollment);
                attendance.setClassDate(LocalDate.of(2026, 4, 1).plusDays(attendanceDays + day));
                attendance.setStatus(day < present ? AttendanceStatus.PRESENT : AttendanceStatus.ABSENT);
                attendance.setRecordedBy(instructor);
                entityManager.persist(attendance);
            }
        });
    }

    private void addOverdueInstallment() {
        transactions.executeWithoutResult(status -> {
            ProgramEnrollment enrollment = entityManager.find(ProgramEnrollment.class, enrollmentId);
            BillingTestFixtures.invoice().forProgramEnrollment(enrollment).ofType(FeeType.MONTHLY_INSTALLMENT)
                    .forPeriod(YearMonth.of(2026, 3)).dueOn(LocalDate.of(2026, 3, 10)).withBaseAmount(ars(15000)).build();
        });
    }

    private ResultActions eligibility(String token) throws Exception {
        return mockMvc.perform(get("/me/procedures/APPROVED_SUBJECTS_CERTIFICATE/eligibility")
                .header("Authorization", "Bearer " + token));
    }

    @Test
    void regularStudentWithApprovedSubjectsIsEligible() throws Exception {
        eligibility(studentToken).andExpect(status().isOk()).andExpect(jsonPath("$.type").value("APPROVED_SUBJECTS_CERTIFICATE"))
                .andExpect(jsonPath("$.eligible").value(true)).andExpect(jsonPath("$.enrollmentId").value(enrollmentId))
                .andExpect(jsonPath("$.reasons").isEmpty());
    }

    @Test
    void nonRegularStudentIsBlockedWithNotRegularStudent() throws Exception {
        recordAttendance(0, 40);

        eligibility(studentToken).andExpect(jsonPath("$.eligible").value(false))
                .andExpect(jsonPath("$.reasons[0].code").value("NOT_REGULAR_STUDENT"));
    }

    @Test
    void billingOffIgnoresDebt() throws Exception {
        addOverdueInstallment();

        eligibility(studentToken).andExpect(jsonPath("$.eligible").value(true));
    }

    @Test
    void billingMirrorBlocksWithPendingPayments() throws Exception {
        setBillingMode("MIRROR");

        eligibility(studentToken).andExpect(jsonPath("$.eligible").value(false))
                .andExpect(jsonPath("$.reasons[0].code").value("PENDING_PAYMENTS"));
    }

    @Test
    void billingFullBlocksOnlyWhenThereIsOverdueDebt() throws Exception {
        setBillingMode("FULL");
        eligibility(studentToken).andExpect(jsonPath("$.eligible").value(true));

        addOverdueInstallment();

        eligibility(studentToken).andExpect(jsonPath("$.eligible").value(false))
                .andExpect(jsonPath("$.reasons[0].code").value("PENDING_PAYMENTS"));
    }

    @Test
    void dailyLimitBlocksAfterAnIssuance() throws Exception {
        issuer.issue(studentId, enrollmentId, personId, NOW);

        eligibility(studentToken).andExpect(jsonPath("$.eligible").value(false))
                .andExpect(jsonPath("$.reasons[0].code").value("ISSUANCE_LIMIT_REACHED"));
        assertThat(certificateRepository.findByStudentIdOrderByIssuedAtDesc(studentId)).hasSize(1);
    }

    @Test
    void studentWithoutApprovedSubjectsIsBlocked() throws Exception {
        jdbc.update("delete from subject_equivalence where program_enrollment_id = ?", enrollmentId);

        eligibility(studentToken).andExpect(jsonPath("$.eligible").value(false))
                .andExpect(jsonPath("$.reasons[0].code").value("NO_SUBJECTS_IN_PROGRESS"));
    }

    @Test
    void ambiguousActiveProgramsAskToChooseOne() throws Exception {
        transactions.executeWithoutResult(status -> {
            CycleTestFixtures fixtures = new CycleTestFixtures(entityManager);
            ProgramEnrollment second = new ProgramEnrollment();
            second.setStudent(entityManager.find(ProgramEnrollment.class, enrollmentId).getStudent());
            second.setProgram(fixtures.program());
            second.setStatus(ProgramEnrollmentStatus.ENROLLED);
            second.setEnrollmentDate(LocalDateTime.of(2026, 3, 2, 10, 0));
            entityManager.persist(second);
        });

        eligibility(studentToken).andExpect(jsonPath("$.eligible").value(false))
                .andExpect(jsonPath("$.reasons[0].code").value("NO_ACTIVE_ENROLLMENT"));
    }

    @Test
    void anotherStudentsEnrollmentIsNotFound() throws Exception {
        mockMvc.perform(get("/me/procedures/APPROVED_SUBJECTS_CERTIFICATE/eligibility").param("enrollmentId", enrollmentId.toString())
                .header("Authorization", "Bearer " + strangerToken)).andExpect(status().isNotFound());
    }
}
