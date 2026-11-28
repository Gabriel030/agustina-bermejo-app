package com.red_escuela.backend.graduation.procedure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
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
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.time.MutableClock;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
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
class OwnExamCertificateIT extends AbstractIntegrationTest {

    private static final String PATH = "/me/documents/EXAM_CERTIFICATE";

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

    private Long registrationId;
    private Long sectionEnrollmentId;
    private Long subjectId;
    private Long termId;
    private Long enrollmentId;
    private String studentToken;
    private String strangerToken;

    @BeforeEach
    void seed() {
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2026, 11, 25, 10, 0));
        transactions.executeWithoutResult(status -> {
            CycleTestFixtures fixtures = new CycleTestFixtures(entityManager);
            Institution institution = new Institution();
            institution.setName("Instituto de prueba");
            entityManager.persist(institution);
            Person studentPerson = persistPerson();
            Student student = persistStudent(studentPerson, institution);
            Person strangerPerson = persistPerson();
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
            entityManager.flush();
            enrollmentId = enrollment.getId();
            sectionEnrollmentId = sectionEnrollment.getId();
            subjectId = subject.getId();
            termId = term.getId();
            studentToken = jwtService.generateAccessToken(studentPerson);
            strangerToken = jwtService.generateAccessToken(strangerPerson);
        });
        registrationId = registrationOn(LocalDateTime.of(2026, 11, 20, 9, 0), ExamBoardStatus.CLOSED, ExamBoardRegistrationStatus.GRADED);
        deleteCertificates();
    }

    @AfterEach
    void cleanUp() {
        deleteCertificates();
    }

    private Long registrationOn(LocalDateTime examDate, ExamBoardStatus boardStatus, ExamBoardRegistrationStatus registrationStatus) {
        return transactions.execute(status -> {
            ExamBoard board = new ExamBoard();
            board.setSubject(entityManager.find(Subject.class, subjectId));
            board.setTerm(entityManager.find(Term.class, termId));
            board.setExamDate(examDate);
            board.setStatus(boardStatus);
            entityManager.persist(board);
            ExamBoardRegistration registration = new ExamBoardRegistration();
            registration.setExamBoard(board);
            registration.setProgramSubjectSectionEnrollment(entityManager.find(SubjectSectionEnrollment.class, sectionEnrollmentId));
            registration.setStatus(registrationStatus);
            registration.setRegisteredAt(LocalDateTime.of(2026, 11, 1, 10, 0));
            entityManager.persist(registration);
            entityManager.flush();
            return registration.getId();
        });
    }

    private Student persistStudent(Person person, Institution institution) {
        Student student = new Student();
        student.setPerson(person);
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        return student;
    }

    private Person persistPerson() {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        String suffix = UUID.randomUUID().toString();
        person.setName("Persona");
        person.setLastname(suffix);
        person.setDni(suffix.substring(0, 12));
        person.setUsername("ownexamcert." + suffix);
        person.setEmail("ownexamcert." + suffix + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        Role role = roleRepository.findByName("ALUMNO").orElseThrow();
        person.getRoles().add(role);
        entityManager.persist(person);
        entityManager.flush();
        return person;
    }

    private void deleteCertificates() {
        jdbc.update("delete from verifiable_document where document_type = 'EXAM_CERTIFICATE'");
        jdbc.update("delete from exam_certificate");
    }

    private ResultActions generate(String token, Long registration) throws Exception {
        return mockMvc.perform(post(PATH).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"registrationId\":" + registration + "}"));
    }

    private ResultActions download(String token, Long id) throws Exception {
        return mockMvc.perform(get("/me/procedures/approved/EXAM_CERTIFICATE/{id}/pdf", id).header("Authorization", "Bearer " + token));
    }

    private long certificateCount() {
        return jdbc.queryForObject("select count(*) from exam_certificate", Long.class);
    }

    private Long issuedId(String token, Long registration) throws Exception {
        MvcResult result = generate(token, registration).andExpect(status().isCreated()).andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    @Test
    void studentGeneratesOwnExamCertificateWithThirtyDaysOfValidity() throws Exception {
        generate(studentToken, registrationId).andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentType").value("EXAM_CERTIFICATE"))
                .andExpect(jsonPath("$.status").value("VALID"))
                .andExpect(jsonPath("$.expiresAt").value("2026-12-25T10:00:00"));

        assertThat(jdbc.queryForObject("select origin from exam_certificate", String.class)).isEqualTo("SELF_SERVICE");
        assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'DOCUMENT_GENERATED_BY_STUDENT' "
                + "and entity_id in (select id from exam_certificate)", Long.class)).isEqualTo(1L);
    }

    @Test
    void generatedCertificateIsDownloadableListedAndVerifiableByCode() throws Exception {
        Long id = issuedId(studentToken, registrationId);

        MvcResult pdf = download(studentToken, id).andExpect(status().isOk()).andReturn();
        assertThat(new String(pdf.getResponse().getContentAsByteArray(), 0, 4)).isEqualTo("%PDF");
        mockMvc.perform(get("/me/procedures/approved").header("Authorization", "Bearer " + studentToken)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.documentType == 'EXAM_CERTIFICATE' && @.id == " + id + ")].status").value("VALID"))
                .andExpect(jsonPath("$[?(@.documentType == 'EXAM_CERTIFICATE' && @.id == " + id + ")].verifiable").value(true));
        String code = jdbc.queryForObject("select code from exam_certificate where id = ?", String.class, id);
        mockMvc.perform(get("/public/documents/{code}", code).with(request -> {
            request.setRemoteAddr("198.51.100.77");
            return request;
        })).andExpect(status().isOk());
    }

    @Test
    void anotherStudentsRegistrationIsNotFoundAndIssuesNothing() throws Exception {
        generate(strangerToken, registrationId).andExpect(status().isNotFound());

        assertThat(certificateCount()).isZero();
    }

    @Test
    void anotherStudentCannotDownloadOwnersCertificate() throws Exception {
        Long id = issuedId(studentToken, registrationId);

        download(strangerToken, id).andExpect(status().isNotFound());
    }

    @Test
    void registrationWithoutRecordedAttendanceIsNotFound() throws Exception {
        Long absent = registrationOn(LocalDateTime.of(2026, 11, 21, 9, 0), ExamBoardStatus.CLOSED, ExamBoardRegistrationStatus.ABSENT);
        Long pending = registrationOn(LocalDateTime.of(2026, 11, 22, 9, 0), ExamBoardStatus.SCHEDULED, ExamBoardRegistrationStatus.REGISTERED);

        generate(studentToken, absent).andExpect(status().isNotFound());
        generate(studentToken, pending).andExpect(status().isNotFound());
        assertThat(certificateCount()).isZero();
    }

    @Test
    void secondRequestForTheSameRegistrationReusesTheValidCertificate() throws Exception {
        Long first = issuedId(studentToken, registrationId);
        Long second = issuedId(studentToken, registrationId);

        assertThat(second).isEqualTo(first);
        assertThat(certificateCount()).isEqualTo(1L);
    }

    @Test
    void dailyLimitBlocksANewCertificateForAnotherRegistration() throws Exception {
        issuedId(studentToken, registrationId);
        Long other = registrationOn(LocalDateTime.of(2026, 11, 21, 9, 0), ExamBoardStatus.CLOSED, ExamBoardRegistrationStatus.GRADED);

        generate(studentToken, other).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("ISSUANCE_LIMIT_REACHED"));
        assertThat(certificateCount()).isEqualTo(1L);
    }

    @Test
    void eligibilityIsOpenForAttendedExamAndNotFoundForForeignRegistration() throws Exception {
        mockMvc.perform(get("/me/procedures/EXAM_CERTIFICATE/eligibility").param("enrollmentId", String.valueOf(enrollmentId))
                        .param("registrationId", String.valueOf(registrationId)).header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.eligible").value(true));
        mockMvc.perform(get("/me/procedures/EXAM_CERTIFICATE/eligibility").param("registrationId", String.valueOf(registrationId))
                .header("Authorization", "Bearer " + strangerToken)).andExpect(status().isNotFound());
        mockMvc.perform(get("/me/procedures/EXAM_CERTIFICATE/eligibility").header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.eligible").value(false))
                .andExpect(jsonPath("$.reasons[0].code").value("EXAM_ATTENDANCE_NOT_RECORDED"));
    }
}
