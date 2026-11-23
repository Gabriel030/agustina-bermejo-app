package com.red_escuela.backend.graduation.certificate.examregistration;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistration;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistrationStatus;
import com.red_escuela.backend.evaluation.entity.ExamBoardStatus;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.platform.infrastructure.FeatureSnapshotHolder;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.time.MutableClock;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.UUID;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class ExamRegistrationEligibilityIT extends AbstractIntegrationTest {

    private static final String PATH = "/me/procedures/EXAM_REGISTRATION_CERTIFICATE/eligibility";

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

    private Long enrollmentId;
    private Long registrationId;
    private String studentToken;
    private String strangerToken;

    @BeforeEach
    void seed() {
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 11, 10, 10, 0));
        transactions.executeWithoutResult(status -> {
            CycleTestFixtures fixtures = new CycleTestFixtures(entityManager);
            Institution institution = new Institution();
            institution.setName("Instituto de prueba");
            entityManager.persist(institution);
            Person studentPerson = persistPerson("ALUMNO");
            Student student = persistStudent(studentPerson, institution);
            Person strangerPerson = persistPerson("ALUMNO");
            persistStudent(strangerPerson, institution);
            Program program = fixtures.program();
            ProgramEnrollment enrollment = new ProgramEnrollment();
            enrollment.setStudent(student);
            enrollment.setProgram(program);
            enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
            enrollment.setEnrollmentDate(LocalDateTime.of(2026, 3, 1, 10, 0));
            entityManager.persist(enrollment);
            Subject subject = fixtures.subject();
            Term term = fixtures.term(2026);
            SubjectSection section = fixtures.section(subject, term, StatusType.ACTIVE, null);
            SubjectSectionEnrollment sectionEnrollment = new SubjectSectionEnrollment();
            sectionEnrollment.setStudent(student);
            sectionEnrollment.setProgramEnrollment(enrollment);
            sectionEnrollment.setSubjectSection(section);
            sectionEnrollment.setStatus(EnrollmentStatus.REGULAR);
            sectionEnrollment.setEnrolledAt(LocalDateTime.of(2026, 3, 1, 10, 0));
            sectionEnrollment.setAttemptNumber(1);
            entityManager.persist(sectionEnrollment);
            ExamBoard board = new ExamBoard();
            board.setSubject(subject);
            board.setTerm(term);
            board.setExamDate(LocalDateTime.of(2026, 11, 20, 9, 0));
            board.setStatus(ExamBoardStatus.SCHEDULED);
            entityManager.persist(board);
            ExamBoardRegistration registration = new ExamBoardRegistration();
            registration.setExamBoard(board);
            registration.setProgramSubjectSectionEnrollment(sectionEnrollment);
            registration.setStatus(ExamBoardRegistrationStatus.REGISTERED);
            registration.setRegisteredAt(LocalDateTime.of(2026, 11, 1, 10, 0));
            entityManager.persist(registration);
            entityManager.flush();
            enrollmentId = enrollment.getId();
            registrationId = registration.getId();
            studentToken = jwtService.generateAccessToken(studentPerson);
            strangerToken = jwtService.generateAccessToken(strangerPerson);
        });
        setBillingMode("OFF");
    }

    @AfterEach
    void restoreBillingMode() {
        setBillingMode("FULL");
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
        person.setUsername("examreg." + suffix);
        person.setEmail("examreg." + suffix + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        Role role = roleRepository.findByName(roleName).orElseThrow();
        person.getRoles().add(role);
        entityManager.persist(person);
        entityManager.flush();
        return person;
    }

    private void addOverdueInstallment() {
        transactions.executeWithoutResult(status -> {
            ProgramEnrollment enrollment = entityManager.find(ProgramEnrollment.class, enrollmentId);
            BillingTestFixtures.invoice().forProgramEnrollment(enrollment).ofType(FeeType.MONTHLY_INSTALLMENT)
                    .forPeriod(YearMonth.of(2026, 3)).dueOn(LocalDate.of(2026, 3, 10)).withBaseAmount(ars(15000)).build();
        });
    }

    private ResultActions eligibility(String token, Long registration) throws Exception {
        var request = get(PATH).header("Authorization", "Bearer " + token);
        if (registration != null) {
            request = request.param("registrationId", registration.toString());
        }
        return mockMvc.perform(request);
    }

    @Test
    void registrationInsideTheWindowWithBillingOffIsEligible() throws Exception {
        eligibility(studentToken, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("EXAM_REGISTRATION_CERTIFICATE"))
                .andExpect(jsonPath("$.eligible").value(true))
                .andExpect(jsonPath("$.reasons").isEmpty());
    }

    @Test
    void explicitOwnRegistrationIsEligible() throws Exception {
        eligibility(studentToken, registrationId).andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(true));
    }

    @Test
    void examBeyondTheWindowIsNotYetEnabled() throws Exception {
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 11, 4, 10, 0));

        eligibility(studentToken, registrationId).andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(false))
                .andExpect(jsonPath("$.reasons[0].code").value("NO_OPEN_ENROLLMENT_WINDOW"));
    }

    @Test
    void overdueDebtInFullModeBlocksWithPendingPayments() throws Exception {
        setBillingMode("FULL");
        addOverdueInstallment();

        eligibility(studentToken, registrationId).andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(false))
                .andExpect(jsonPath("$.reasons[0].code").value("PENDING_PAYMENTS"))
                .andExpect(jsonPath("$.reasons[0].nextStep.kind").value("GO_TO_PAYMENTS"));
    }

    @Test
    void mirrorModeSendsTheStudentToAdministration() throws Exception {
        setBillingMode("MIRROR");

        eligibility(studentToken, registrationId).andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(false))
                .andExpect(jsonPath("$.reasons[0].code").value("PENDING_PAYMENTS"))
                .andExpect(jsonPath("$.reasons[0].nextStep.kind").value("CONTACT_ADMINISTRATION"));
    }

    @Test
    void anotherStudentsRegistrationIsNotFound() throws Exception {
        eligibility(strangerToken, registrationId).andExpect(status().isNotFound());
    }

    @Test
    void studentWithoutRegistrationIsToldToRegister() throws Exception {
        eligibility(strangerToken, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(false))
                .andExpect(jsonPath("$.reasons[0].code").value("NOT_REGISTERED_FOR_EXAM"));
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    }
}
