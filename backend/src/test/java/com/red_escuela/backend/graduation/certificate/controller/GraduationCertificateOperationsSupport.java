package com.red_escuela.backend.graduation.certificate.controller;

import com.jayway.jsonpath.JsonPath;
import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.graduation.certificate.repository.GraduationCertificateRepository;
import com.red_escuela.backend.graduation.certificate.source.GraduationDebtCheck;
import com.red_escuela.backend.identity.entity.AuthProvider;
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
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class GraduationCertificateOperationsSupport extends AbstractIntegrationTest {

    static final String BASE = "/graduation-certificates";
    static final String VALID_REASON = "{\"reason\":\"Error en el apellido del titular\"}";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    EntityManager entityManager;
    @Autowired
    RoleRepository roleRepository;
    @Autowired
    JwtService jwtService;
    @Autowired
    GraduationCertificateRepository repository;
    @MockitoBean
    GraduationDebtCheck debtCheck;

    CycleTestFixtures fixtures;
    String director;
    String administrative;

    @BeforeEach
    void createUsers() {
        fixtures = new CycleTestFixtures(entityManager);
        director = tokenFor("DIRECTOR", 1);
        administrative = tokenFor("ADMINISTRATIVO", 2);
    }

    String tokenFor(String roleName, int index) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setName("Operacion");
        person.setLastname(roleName);
        person.setUsername("graduation.ops." + roleName.toLowerCase());
        person.setDni(String.valueOf(93_000_000 + index));
        person.setEmail("graduation.ops." + roleName.toLowerCase() + "@example.com");
        person.setPassword("not-used");
        person.setAuthProvider(AuthProvider.LOCAL);
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        Role role = roleRepository.findByName(roleName).orElseThrow();
        person.getRoles().add(role);
        entityManager.persist(person);
        entityManager.flush();
        return jwtService.generateAccessToken(person);
    }

    ProgramEnrollment completedEnrollment() {
        Institution institution = new Institution();
        institution.setName("Instituto de prueba");
        entityManager.persist(institution);
        Student graduate = new Student();
        graduate.setPerson(fixtures.person());
        graduate.setInstitution(institution);
        graduate.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(graduate);
        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setStudent(graduate);
        enrollment.setProgram(fixtures.program());
        enrollment.setStatus(ProgramEnrollmentStatus.COMPLETED);
        enrollment.setEnrollmentDate(LocalDateTime.now().minusYears(3));
        enrollment.setCompletionDate(LocalDateTime.of(2026, 7, 15, 10, 0));
        entityManager.persist(enrollment);
        entityManager.flush();
        return enrollment;
    }

    ResultActions send(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token));
    }

    ResultActions postJson(String path, String token, String body) throws Exception {
        return send(post(path).contentType(MediaType.APPLICATION_JSON).content(body), token);
    }

    Long issueCertificate(ProgramEnrollment enrollment) throws Exception {
        MvcResult result = postJson(BASE, director, "{\"kind\":\"PROGRAM\",\"enrollmentId\":" + enrollment.getId() + "}")
                .andExpect(status().isCreated()).andReturn();
        String code = JsonPath.read(result.getResponse().getContentAsString(), "$.code");
        return repository.findByCode(code).orElseThrow().getId();
    }
}
