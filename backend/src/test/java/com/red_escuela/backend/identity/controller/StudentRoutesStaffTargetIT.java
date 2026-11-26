package com.red_escuela.backend.identity.controller;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.EmailVerificationTokenRepository;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StudentRoutesStaffTargetIT extends AbstractIntegrationTest {

    private static final long MISSING_STUDENT_ID = 999_999L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    private Person persistPerson(String username, String dni, String... roleNames) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setName("Nombre");
        person.setLastname("Apellido");
        person.setDni(dni);
        person.setUsername(username);
        person.setEmail(username + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        for (String roleName : roleNames) {
            person.getRoles().add(roleRepository.findByName(roleName).orElseThrow());
        }
        entityManager.persist(person);
        entityManager.flush();
        return person;
    }

    private Student persistStudentOf(Person person) {
        Institution institution = new Institution();
        institution.setName("Instituto " + person.getUsername());
        entityManager.persist(institution);
        Student student = new Student();
        student.setPerson(person);
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        entityManager.flush();
        return student;
    }

    private String administrativeToken() {
        return jwtService.generateAccessToken(persistPerson("admin.actor", "40000001", "ADMINISTRATIVO"));
    }

    private MockHttpServletResponse send(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)).andReturn().getResponse();
    }

    private String updateBody(String email) {
        return "{\"firstName\":\"Lucía\",\"lastName\":\"Gómez\",\"documentNumber\":\"40999888\",\"email\":\"" + email + "\"}";
    }

    @Test
    void staffCannotChangeTheEmailOfAStudentRecordThatBelongsToADirector() throws Exception {
        Person director = persistPerson("director.student", "40000002", "DIRECTOR", "ALUMNO");
        Student student = persistStudentOf(director);

        MockHttpServletResponse response = send(put("/students/{id}", student.getId()).content(updateBody("attacker@example.com")),
                administrativeToken());

        assertThat(response.getStatus()).isEqualTo(404);
        entityManager.clear();
        Person reloaded = entityManager.find(Person.class, director.getId());
        assertThat(reloaded.emailValue()).isEqualTo("director.student@example.com");
        assertThat(emailVerificationTokenRepository.findAll()).isEmpty();
    }

    @Test
    void staffCannotWithdrawTheStudentRecordOfADirector() throws Exception {
        Person director = persistPerson("director.withdraw", "40000003", "DIRECTOR", "ALUMNO");
        Student student = persistStudentOf(director);

        MockHttpServletResponse response = send(post("/students/{id}/withdraw", student.getId())
                .content("{\"reason\":\"OTRO\",\"detail\":\"x\"}"), administrativeToken());

        assertThat(response.getStatus()).isEqualTo(404);
        entityManager.clear();
        assertThat(entityManager.find(Person.class, director.getId()).getStatus()).isEqualTo(StatusType.ACTIVE);
    }

    @Test
    void staffCannotReactivateOrResetThePasswordOfATeacherStudentRecord() throws Exception {
        Person teacher = persistPerson("teacher.student", "40000004", "DOCENTE", "ALUMNO");
        Student student = persistStudentOf(teacher);
        String token = administrativeToken();

        MockHttpServletResponse reactivate = send(post("/students/{id}/reactivate", student.getId())
                .content("{\"reason\":\"Motivo suficiente\"}"), token);
        MockHttpServletResponse resetPassword = send(post("/students/{id}/reset-password", student.getId()), token);
        MockHttpServletResponse missing = send(post("/students/{id}/reset-password", MISSING_STUDENT_ID), token);

        assertThat(reactivate.getStatus()).isEqualTo(404);
        assertThat(resetPassword.getStatus()).isEqualTo(404);
        assertThat(resetPassword.getContentAsString()).contains("STUDENT_NOT_FOUND");
        assertThat(missing.getContentAsString()).contains("STUDENT_NOT_FOUND");
    }

    @Test
    void staffStillManagesAStudentThatOnlyHoldsTheStudentRole() throws Exception {
        Person onlyStudent = persistPerson("only.student", "40000005", "ALUMNO");
        Student student = persistStudentOf(onlyStudent);

        MockHttpServletResponse response = send(put("/students/{id}", student.getId()).content(updateBody("only.student@example.com")),
                administrativeToken());

        assertThat(response.getStatus()).isEqualTo(200);
    }
}
