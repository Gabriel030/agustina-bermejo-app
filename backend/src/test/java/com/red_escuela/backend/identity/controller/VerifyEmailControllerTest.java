package com.red_escuela.backend.identity.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.dto.CreateProspectRequest;
import com.red_escuela.backend.academicstructure.entity.Institution;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.identity.entity.EmailVerificationToken;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.EmailVerificationTokenRepository;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.util.TokenHashUtil;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class VerifyEmailControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JwtService jwtService;

    private Long persistInstitution() {
        Institution institution = new Institution();
        institution.setName("Instituto de prueba");
        entityManager.persist(institution);
        entityManager.flush();
        return institution.getId();
    }

    private Student persistProspecto(Long institutionId) {
        return persistProspecto(institutionId, "maria.lopez.ver." + System.nanoTime() + "@example.com");
    }

    private Student persistProspecto(Long institutionId, String email) {
        Address address = new Address();
        entityManager.persist(address);

        Person person = new Person();
        person.setName("María");
        person.setLastname("López");
        person.setEmail(email);
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        entityManager.persist(person);

        Institution institution = entityManager.find(Institution.class, institutionId);

        Student student = new Student();
        student.setPerson(person);
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.PROSPECTO);
        entityManager.persist(student);
        entityManager.flush();
        return student;
    }

    private String persistVerificationToken(Person person, boolean expired) {
        String clear = "token-verificacion-test-" + System.nanoTime();
        EmailVerificationToken token = new EmailVerificationToken();
        token.setPerson(person);
        token.setTokenHash(TokenHashUtil.sha256Hex(clear));
        token.setExpiresAt(expired
                ? LocalDateTime.now().minusHours(1)
                : LocalDateTime.now().plusHours(48));
        entityManager.persist(token);
        entityManager.flush();
        return clear;
    }

    private String tokenWithRole(String username, String roleName) {
        Address address = new Address();
        entityManager.persist(address);

        Person person = new Person();
        person.setUsername(username);
        person.setEmail(username + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);

        Role role = roleRepository.findByName(roleName).orElseThrow();
        person.getRoles().add(role);

        entityManager.persist(person);
        entityManager.flush();

        return jwtService.generateAccessToken(person);
    }

    @Test
    void tokenValidoMarcaEmailVerificadoYEliminaToken() throws Exception {
        Long institutionId = persistInstitution();
        Student student = persistProspecto(institutionId);
        String clear = persistVerificationToken(student.getPerson(), false);

        mockMvc.perform(get("/verify-email").param("token", clear))
                .andExpect(status().isOk());

        entityManager.flush();
        entityManager.refresh(student.getPerson());
        assertThat(student.getPerson().getEmailVerified()).isTrue();
        assertThat(emailVerificationTokenRepository.findAll()).isEmpty();
    }

    @Test
    void tokenExpiradoDevuelve400() throws Exception {
        Long institutionId = persistInstitution();
        Student student = persistProspecto(institutionId);
        String clear = persistVerificationToken(student.getPerson(), true);

        mockMvc.perform(get("/verify-email").param("token", clear))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tokenInexistenteDevuelve400() throws Exception {
        mockMvc.perform(get("/verify-email").param("token", "token-que-no-existe"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void crearProspectoAnonimoCreaTokenDeVerificacion() throws Exception {
        Long institutionId = persistInstitution();
        CreateProspectRequest request = new CreateProspectRequest(
                "Carlos", "Ruiz", "carlos.ruiz.ver@example.com", null, institutionId);

        mockMvc.perform(post("/prospects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.emailVerified").isEmpty());

        assertThat(emailVerificationTokenRepository.findAll()).hasSize(1);
    }

    @Test
    void crearProspectoComoPreceptorNoGeneraTokenDeVerificacion() throws Exception {
        Long institutionId = persistInstitution();
        String token = tokenWithRole("preceptor.ver1", "PRECEPTOR");
        CreateProspectRequest request = new CreateProspectRequest(
                "Luis", "García", "luis.garcia.ver@example.com", null, institutionId);

        mockMvc.perform(post("/prospects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        assertThat(emailVerificationTokenRepository.findAll()).isEmpty();
    }

    @Test
    void saveDraftPersisteDniYDomicilio() throws Exception {
        Long institutionId = persistInstitution();
        Student student = persistProspecto(institutionId);
        String token = tokenWithRole("preceptor.ver2", "PRECEPTOR");

        String body = """
                {"dni":"44555666","street":"Av. Corrientes","number":"1234","city":"Buenos Aires","state":"CABA"}
                """;

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/prospects/{id}", student.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(body))
                .andExpect(status().isNoContent());

        entityManager.flush();
        entityManager.refresh(student.getPerson());
        assertThat(student.getPerson().getDni()).isEqualTo("44555666");
        assertThat(student.getPerson().getAddress().getCity()).isEqualTo("Buenos Aires");
    }

    @Test
    void saveDraftConDniDuplicadoDevuelve400() throws Exception {
        Long institutionId = persistInstitution();
        Student student1 = persistProspecto(institutionId, "estudiante1.ver@example.com");
        student1.getPerson().setDni("99888777");
        entityManager.flush();

        Student student2 = persistProspecto(institutionId, "estudiante2.ver@example.com");
        entityManager.flush();

        String token = tokenWithRole("preceptor.ver3", "PRECEPTOR");
        String body = """
                {"dni":"99888777","street":"Calle Falsa","number":"123","city":"Ciudad"}
                """;

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/prospects/{id}", student2.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reenviarVerificacionGeneraNuevoTokenYEliminaElAnterior() throws Exception {
        Long institutionId = persistInstitution();
        Student student = persistProspecto(institutionId);
        persistVerificationToken(student.getPerson(), false);
        assertThat(emailVerificationTokenRepository.findAll()).hasSize(1);

        String token = tokenWithRole("preceptor.ver4", "PRECEPTOR");

        mockMvc.perform(post("/prospects/{id}/send-verification", student.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(emailVerificationTokenRepository.findAll()).hasSize(1);
    }
}
