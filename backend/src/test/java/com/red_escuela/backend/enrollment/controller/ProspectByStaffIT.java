package com.red_escuela.backend.enrollment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.dto.ConfirmProspectRequest;
import com.red_escuela.backend.enrollment.dto.CreateProspectRequest;
import com.red_escuela.backend.enrollment.entity.DocumentType;
import com.red_escuela.backend.enrollment.entity.StudentDocument;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class ProspectByStaffIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TransactionTemplate transactions;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private RoleRepository roleRepository;

    private Long institutionId;
    private Person director;
    private String directorToken;

    @BeforeEach
    void createInstitutionAndDirector() {
        transactions.executeWithoutResult(status -> {
            Institution institution = new Institution();
            institution.setName("Instituto de prueba");
            entityManager.persist(institution);
            institutionId = institution.getId();
            director = persistPerson("DIRECTOR");
            directorToken = jwtService.generateAccessToken(director);
        });
    }

    private Person persistPerson(String roleName) {
        Address address = new Address();
        entityManager.persist(address);
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Person person = new Person();
        person.setUsername(roleName.toLowerCase() + suffix);
        person.setDni(Long.toString(Math.abs((long) suffix.hashCode())));
        person.setName("Persona");
        person.setLastname("Prueba");
        person.setEmail(roleName.toLowerCase() + suffix + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        Role role = roleRepository.findByName(roleName).orElseThrow();
        person.getRoles().add(role);
        entityManager.persist(person);
        return person;
    }

    private String token(String roleName) {
        return transactions.execute(status -> jwtService.generateAccessToken(persistPerson(roleName)));
    }

    private String createBody(String email) throws Exception {
        return objectMapper.writeValueAsString(new CreateProspectRequest("Ana", "Paz", email, "1155550000", institutionId));
    }

    private Long createByStaff(String email) throws Exception {
        String response = mockMvc.perform(post("/prospects/by-staff")
                        .header("Authorization", "Bearer " + directorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    @Test
    void directorCreatesProspectByStaffAndTheActorIsStored() throws Exception {
        Long studentId = createByStaff("by-staff-" + UUID.randomUUID() + "@example.com");

        Long registeredBy = jdbc.queryForObject(
                "select registered_by_person_id from student where id = ?", Long.class, studentId);
        assertThat(registeredBy).isEqualTo(director.getId());
    }

    @Test
    void publicCreationLeavesTheActorNull() throws Exception {
        String response = mockMvc.perform(post("/prospects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("public-" + UUID.randomUUID() + "@example.com")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long studentId = objectMapper.readTree(response).get("id").asLong();

        Long registeredBy = jdbc.queryForObject(
                "select registered_by_person_id from student where id = ?", Long.class, studentId);
        assertThat(registeredBy).isNull();
    }

    @Test
    void byStaffRejectsAnonymousAndStudentRoles() throws Exception {
        String body = createBody("rejected-" + UUID.randomUUID() + "@example.com");

        mockMvc.perform(post("/prospects/by-staff").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/prospects/by-staff")
                        .header("Authorization", "Bearer " + token("ALUMNO"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void byStaffWithAnExistingEmailReturnsConflict() throws Exception {
        String email = "dup-" + UUID.randomUUID() + "@example.com";
        createByStaff(email);

        mockMvc.perform(post("/prospects/by-staff")
                        .header("Authorization", "Bearer " + directorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(email)))
                .andExpect(status().isConflict());
    }

    @Test
    void directorCanConfirmAProspectWithoutAnyDocument() throws Exception {
        Long studentId = createByStaff("confirm-" + UUID.randomUUID() + "@example.com");
        String dni = Long.toString(10_000_000L + Math.abs(UUID.randomUUID().getMostSignificantBits() % 80_000_000L));
        ConfirmProspectRequest request = new ConfirmProspectRequest(dni, null, "Calle", "123", null, "Ciudad", null, null, null);

        mockMvc.perform(post("/prospects/{id}/confirm", studentId)
                        .header("Authorization", "Bearer " + directorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboardingStatus").value("CONFIRMADO"));

        String status = jdbc.queryForObject("select onboarding_status from student where id = ?", String.class, studentId);
        assertThat(status).isEqualTo(StudentOnboardingStatus.CONFIRMADO.name());
    }

    @Test
    void listProspectsReturnsMissingDocumentsAndSkipsConfirmedStudents() throws Exception {
        String email = "list-" + UUID.randomUUID() + "@example.com";
        Long withDocument = createByStaff(email);
        Long confirmedId = transactions.execute(status -> {
            Student confirmed = new Student();
            confirmed.setPerson(persistPerson("ALUMNO"));
            confirmed.setInstitution(entityManager.find(Institution.class, institutionId));
            confirmed.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
            entityManager.persist(confirmed);
            return confirmed.getId();
        });
        transactions.executeWithoutResult(status -> {
            StudentDocument document = new StudentDocument();
            document.setStudent(entityManager.find(Student.class, withDocument));
            document.setType(DocumentType.DNI_FRENTE);
            document.setFilePath("prospects/" + withDocument + "/dni-frente.pdf");
            document.setUploadedAt(LocalDateTime.now());
            entityManager.persist(document);
        });

        String json = mockMvc.perform(get("/prospects").header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var items = objectMapper.readTree(json);
        var listed = java.util.stream.StreamSupport.stream(items.spliterator(), false)
                .filter(item -> item.get("id").asLong() == withDocument)
                .findFirst().orElseThrow();
        assertThat(listed.get("email").asText()).isEqualTo(email);
        assertThat(listed.get("telefono").asText()).isEqualTo("1155550000");
        assertThat(listed.get("emailVerified").asBoolean()).isFalse();
        assertThat(objectMapper.convertValue(listed.get("missingDocuments"), java.util.List.class))
                .containsExactlyInAnyOrder("DNI_DORSO", "ANALITICO_SECUNDARIO");
        assertThat(java.util.stream.StreamSupport.stream(items.spliterator(), false)
                .anyMatch(item -> item.get("id").asLong() == confirmedId)).isFalse();
    }

    @Test
    void listProspectsIsForbiddenForDocente() throws Exception {
        mockMvc.perform(get("/prospects").header("Authorization", "Bearer " + token("DOCENTE")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/prospects")).andExpect(status().isUnauthorized());
    }
}
