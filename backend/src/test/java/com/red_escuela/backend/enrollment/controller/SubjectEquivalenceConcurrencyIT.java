package com.red_escuela.backend.enrollment.controller;

import com.red_escuela.backend.academicstructure.domain.CurricularTerm;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.config.TestSubjectCodes;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectEquivalenceStatus;
import com.red_escuela.backend.enrollment.repository.SubjectEquivalenceRepository;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SubjectEquivalenceConcurrencyIT extends AbstractIntegrationTest {

    private static final AtomicLong SEED_SEQUENCE = new AtomicLong(64_000_000L);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TransactionTemplate transactionTemplate;
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

    @Test
    void twoSimultaneousRecordsForTheSameSubjectLeaveExactlyOneActiveEquivalence() throws Exception {
        long[] ids = transactionTemplate.execute(status -> seed());
        long enrollmentId = ids[0];
        long subjectId = ids[1];
        String token = tokenOf(ids[2]);
        String body = "{\"subjectId\":" + subjectId + ",\"originInstitution\":\"Universidad de origen\","
                + "\"originSubjectName\":\"Materia de origen\",\"recognizedOn\":\"" + LocalDate.now()
                + "\",\"resolutionNumber\":\"RES-1\",\"reason\":\"Estudios previos reconocidos\"}";
        int attempts = 2;
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        List<Future<String>> results = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            results.add(executor.submit(() -> {
                ready.countDown();
                go.await();
                var response = mockMvc.perform(post("/program-enrollments/{id}/equivalences", enrollmentId)
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON).content(body))
                        .andReturn().getResponse();
                return response.getStatus() == 201 ? "CREATED" : response.getStatus() + ":" + response.getContentAsString();
            }));
        }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        go.countDown();
        List<String> outcomes = new ArrayList<>();
        for (Future<String> result : results) {
            outcomes.add(result.get(30, TimeUnit.SECONDS));
        }
        executor.shutdown();

        assertThat(outcomes).hasSize(2).contains("CREATED");
        assertThat(outcomes.stream().filter(outcome -> !outcome.equals("CREATED")).toList())
                .singleElement().asString().startsWith("409:").contains("EQUIVALENCE_ALREADY_ACTIVE");
        assertThat(equivalenceRepository.findByProgramEnrollmentIdAndStatusOrderByRecognizedOnDescIdDesc(enrollmentId,
                SubjectEquivalenceStatus.ACTIVE)).hasSize(1);
    }

    @Test
    void twoSimultaneousRecordsThatCompleteThePlanCreateASingleGraduationRequestAndKeepBothEquivalences() throws Exception {
        long[] ids = transactionTemplate.execute(status -> seed());
        long enrollmentId = ids[0];
        String token = tokenOf(ids[2]);
        int attempts = 2;
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            long subjectId = ids[i == 0 ? 1 : 3];
            String body = "{\"subjectId\":" + subjectId + ",\"originInstitution\":\"Universidad de origen\","
                    + "\"originSubjectName\":\"Materia de origen\",\"recognizedOn\":\"" + LocalDate.now()
                    + "\",\"resolutionNumber\":\"RES-1\",\"reason\":\"Estudios previos reconocidos\"}";
            results.add(executor.submit(() -> {
                ready.countDown();
                go.await();
                return mockMvc.perform(post("/program-enrollments/{id}/equivalences", enrollmentId)
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON).content(body))
                        .andReturn().getResponse().getStatus();
            }));
        }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        go.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> result : results) {
            statuses.add(result.get(30, TimeUnit.SECONDS));
        }
        executor.shutdown();

        assertThat(statuses).containsExactly(201, 201);
        assertThat(equivalenceRepository.findByProgramEnrollmentIdAndStatusOrderByRecognizedOnDescIdDesc(enrollmentId,
                SubjectEquivalenceStatus.ACTIVE)).hasSize(2);
        assertThat(jdbc.queryForObject("select count(*) from graduation_request where program_enrollment_id = ?",
                Integer.class, enrollmentId)).isEqualTo(1);
    }

    @Test
    void twoSimultaneousVoidsOfTheSameEquivalenceLeaveOneVoidedAndOneConflict() throws Exception {
        long[] ids = transactionTemplate.execute(status -> seed());
        long enrollmentId = ids[0];
        String token = tokenOf(ids[2]);
        String recordBody = "{\"subjectId\":" + ids[1] + ",\"originInstitution\":\"Universidad de origen\","
                + "\"originSubjectName\":\"Materia de origen\",\"recognizedOn\":\"" + LocalDate.now()
                + "\",\"resolutionNumber\":\"RES-1\",\"reason\":\"Estudios previos reconocidos\"}";
        String created = mockMvc.perform(post("/program-enrollments/{id}/equivalences", enrollmentId)
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(recordBody))
                .andReturn().getResponse().getContentAsString();
        long equivalenceId = Long.parseLong(created.replaceAll(".*\"id\":(\\d+).*", "$1"));
        int attempts = 2;
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        List<Future<String>> results = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            results.add(executor.submit(() -> {
                ready.countDown();
                go.await();
                var response = mockMvc.perform(post("/program-enrollments/{id}/equivalences/{eid}/void", enrollmentId, equivalenceId)
                                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\":\"Anulación por carga errónea\"}"))
                        .andReturn().getResponse();
                return response.getStatus() == 200 ? "VOIDED" : response.getStatus() + ":" + response.getContentAsString();
            }));
        }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        go.countDown();
        List<String> outcomes = new ArrayList<>();
        for (Future<String> result : results) {
            outcomes.add(result.get(30, TimeUnit.SECONDS));
        }
        executor.shutdown();

        assertThat(outcomes).contains("VOIDED");
        assertThat(outcomes.stream().filter(outcome -> !outcome.equals("VOIDED")).toList())
                .singleElement().asString().startsWith("409:").contains("EQUIVALENCE_ALREADY_VOIDED");
        assertThat(jdbc.queryForList("select status from subject_equivalence where program_enrollment_id = ?", String.class, enrollmentId))
                .containsExactly("VOIDED");
        assertThat(jdbc.queryForObject(
                "select count(*) from audit_log where action = 'SUBJECT_EQUIVALENCE_VOIDED' and entity_type = 'SUBJECT_EQUIVALENCE' and entity_id = ?",
                Integer.class, equivalenceId)).isEqualTo(1);
    }

    private String tokenOf(long personId) {
        Person person = transactionTemplate.execute(status -> {
            Person found = entityManager.find(Person.class, personId);
            found.getRoles().size();
            return found;
        });
        return jwtService.generateAccessToken(person);
    }

    private long[] seed() {
        long sequence = SEED_SEQUENCE.addAndGet(10);
        String suffix = Long.toString(sequence);
        Institution institution = new Institution();
        institution.setName("Instituto concurrente " + suffix);
        entityManager.persist(institution);
        Address studentAddress = new Address();
        entityManager.persist(studentAddress);
        Person studentPerson = new Person();
        studentPerson.setName("Alumno");
        studentPerson.setLastname("Concurrente");
        studentPerson.setDni(sequence + "1");
        studentPerson.setUsername(sequence + "1");
        studentPerson.setEmail(sequence + "1@example.com");
        studentPerson.setAddress(studentAddress);
        studentPerson.setStatus(StatusType.ACTIVE);
        entityManager.persist(studentPerson);
        Student student = new Student();
        student.setPerson(studentPerson);
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        Program program = new Program();
        program.setName("Carrera concurrente " + suffix);
        program.setTotalYears(3);
        program.setTotalCreditsRequired(10);
        entityManager.persist(program);
        Subject subject = new Subject();
        subject.setCode(TestSubjectCodes.next());
        subject.setName("Materia concurrente " + suffix);
        entityManager.persist(subject);
        Subject secondSubject = new Subject();
        secondSubject.setCode(TestSubjectCodes.next());
        secondSubject.setName("Segunda materia concurrente " + suffix);
        entityManager.persist(secondSubject);
        ProgramSubject secondEntry = new ProgramSubject();
        secondEntry.setProgram(program);
        secondEntry.setPlan(com.red_escuela.backend.testsupport.TestCurriculumPlans.planOf(program));
        secondEntry.setSubject(secondSubject);
        secondEntry.setCredits(5);
        secondEntry.setCurricularYear(1);
        secondEntry.setCurricularTerm(CurricularTerm.FIRST);
        secondEntry.setStatus(StatusType.ACTIVE);
        entityManager.persist(secondEntry);
        ProgramSubject planEntry = new ProgramSubject();
        planEntry.setProgram(program);
        planEntry.setPlan(com.red_escuela.backend.testsupport.TestCurriculumPlans.planOf(program));
        planEntry.setSubject(subject);
        planEntry.setCredits(5);
        planEntry.setCurricularYear(1);
        planEntry.setCurricularTerm(CurricularTerm.FIRST);
        planEntry.setStatus(StatusType.ACTIVE);
        entityManager.persist(planEntry);
        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgram(program);
        enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        enrollment.setEnrollmentDate(LocalDateTime.now());
        entityManager.persist(enrollment);
        Address staffAddress = new Address();
        entityManager.persist(staffAddress);
        Person staff = new Person();
        staff.setUsername("equiv-concurrent-admin" + suffix);
        staff.setDni(sequence + "2");
        staff.setName("Persona");
        staff.setLastname("Concurrente");
        staff.setEmail("equiv-concurrent-admin" + suffix + "@example.com");
        staff.setAddress(staffAddress);
        staff.setStatus(StatusType.ACTIVE);
        Role role = roleRepository.findByName("ADMINISTRATIVO").orElseThrow();
        staff.getRoles().add(role);
        entityManager.persist(staff);
        entityManager.flush();
        return new long[] {enrollment.getId(), subject.getId(), staff.getId(), secondSubject.getId()};
    }
}
