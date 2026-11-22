package com.red_escuela.backend.graduation.certificate.controller;

import com.jayway.jsonpath.JsonPath;
import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.graduation.certificate.engine.CertificateFileStore;
import com.red_escuela.backend.graduation.certificate.source.GraduationDebtCheck;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MeGraduationCertificateMismatchAuditIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    EntityManager entityManager;
    @Autowired
    TransactionTemplate transactionTemplate;
    @Autowired
    RoleRepository roleRepository;
    @Autowired
    JwtService jwtService;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    CertificateFileStore fileStore;
    @MockitoBean
    GraduationDebtCheck debtCheck;

    @Test
    void theMismatchAuditSurvivesTheIntegrityErrorResponse() throws Exception {
        String[] tokens = new String[2];
        Long enrollmentId = transactionTemplate.execute(status -> {
            CycleTestFixtures fixtures = new CycleTestFixtures(entityManager);
            Institution institution = new Institution();
            institution.setName("Instituto de prueba");
            entityManager.persist(institution);
            Student student = new Student();
            student.setPerson(fixtures.person());
            student.setInstitution(institution);
            student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
            entityManager.persist(student);
            ProgramEnrollment enrollment = new ProgramEnrollment();
            enrollment.setStudent(student);
            enrollment.setProgram(fixtures.program());
            enrollment.setStatus(ProgramEnrollmentStatus.COMPLETED);
            enrollment.setEnrollmentDate(LocalDateTime.now().minusYears(3));
            enrollment.setCompletionDate(LocalDateTime.of(2026, 7, 15, 10, 0));
            entityManager.persist(enrollment);
            student.getPerson().getRoles().add(roleRepository.findByName("ALUMNO").orElseThrow());
            entityManager.flush();
            tokens[0] = jwtService.generateAccessToken(student.getPerson());
            tokens[1] = jwtService.generateAccessToken(directorPerson(fixtures));
            return enrollment.getId();
        });

        MvcResult issued = mockMvc.perform(post("/graduation-certificates").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + tokens[1])
                        .content("{\"kind\":\"PROGRAM\",\"enrollmentId\":" + enrollmentId + "}"))
                .andExpect(status().isCreated()).andReturn();
        String code = JsonPath.read(issued.getResponse().getContentAsString(), "$.code");
        Long id = jdbc.queryForObject("select id from graduation_certificate where code = ?", Long.class, code);
        fileStore.save(code, new byte[] {1, 2, 3});
        jdbc.update("update graduation_certificate set pdf_sha256 = ? where id = ?", "f".repeat(64), id);

        mockMvc.perform(get("/me/certificates/" + id + "/pdf").header("Authorization", "Bearer " + tokens[0]))
                .andExpect(status().isInternalServerError());

        assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'GRADUATION_CERTIFICATE_PDF_MISMATCH' and entity_id = ?",
                Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'GRADUATION_CERTIFICATE_DOWNLOADED' and entity_id = ?",
                Integer.class, id)).isZero();
    }

    private Person directorPerson(CycleTestFixtures fixtures) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setName("Director");
        person.setLastname("Mismatch");
        person.setUsername("graduation.mismatch.director");
        person.setDni("93555001");
        person.setEmail("graduation.mismatch.director@example.com");
        person.setPassword("not-used");
        person.setAuthProvider(com.red_escuela.backend.identity.entity.AuthProvider.LOCAL);
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        Role role = roleRepository.findByName("DIRECTOR").orElseThrow();
        person.getRoles().add(role);
        entityManager.persist(person);
        entityManager.flush();
        return person;
    }
}
