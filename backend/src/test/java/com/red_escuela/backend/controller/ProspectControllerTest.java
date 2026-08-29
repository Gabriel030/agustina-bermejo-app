package com.red_escuela.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.dto.ConfirmProspectRequest;
import com.red_escuela.backend.dto.CreateProspectRequest;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.entity.Institution;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.PasswordResetToken;
import com.red_escuela.backend.entity.Role;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.entity.Student;
import com.red_escuela.backend.entity.StudentDocument;
import com.red_escuela.backend.entity.StudentOnboardingStatus;
import com.red_escuela.backend.repository.PasswordResetTokenRepository;
import com.red_escuela.backend.repository.RoleRepository;
import com.red_escuela.backend.repository.StudentDocumentRepository;
import com.red_escuela.backend.repository.StudentRepository;
import com.red_escuela.backend.service.AuthService;
import com.red_escuela.backend.service.AuthTokens;
import com.red_escuela.backend.service.JwtService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProspectControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private StudentDocumentRepository studentDocumentRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private AuthService authService;

    @Value("${app.uploads.directory}")
    private String uploadsDirectory;

    private Long persistInstitution() {
        Institution institution = new Institution();
        institution.setName("Instituto de prueba");
        entityManager.persist(institution);
        entityManager.flush();
        return institution.getId();
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

    private Student persistProspecto(Long institutionId) {
        Address address = new Address();
        entityManager.persist(address);

        Person person = new Person();
        person.setName("Ana");
        person.setLastname("Gómez");
        person.setEmail("ana.gomez.prospecto@example.com");
        person.setAddress(address);
        entityManager.persist(person);

        Institution institution = entityManager.find(Institution.class, institutionId);

        // Igual que PersonFactory: status ACTIVE explícito, si no AuthService.login
        // rechaza cualquier login posterior (chequeo agregado en esta misma spec).
        person.setStatus(StatusType.ACTIVE);

        Student student = new Student();
        student.setPerson(person);
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.PROSPECTO);
        entityManager.persist(student);
        entityManager.flush();
        return student;
    }

    @Test
    void crearProspectoSinAuthDevuelve201ConStudentEnProspectoSinUsernameNiDni() throws Exception {
        Long institutionId = persistInstitution();
        CreateProspectRequest request = new CreateProspectRequest(
                "Juan", "Pérez", "juan.perez@example.com", "1122334455", institutionId);

        mockMvc.perform(post("/prospects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.onboardingStatus").value("PROSPECTO"))
                .andExpect(jsonPath("$.username").doesNotExist())
                .andExpect(jsonPath("$.dni").doesNotExist());

        List<Student> students = studentRepository.findAll();
        assertThat(students).hasSize(1);
        assertThat(students.get(0).getOnboardingStatus()).isEqualTo(StudentOnboardingStatus.PROSPECTO);
        assertThat(students.get(0).getPerson().getUsername()).isNull();
        assertThat(students.get(0).getPerson().getDni()).isNull();
    }

    // El caso "PDF de 6MB -> 400" (spring.servlet.multipart.max-file-size) no se puede
    // probar acá: MockMultipartHttpServletRequest no pasa por el parseo real de multipart
    // del servlet container, así que MaxUploadSizeExceededException nunca se dispara contra
    // un mock request (verificado corriendo este mismo test contra MockMvc: da 201, no 400).
    // Se prueba end-to-end con servidor real en ProspectDocumentUploadLimitTest.

    @Test
    void agregarDocumentoConTipoNoSoportadoDevuelve400() throws Exception {
        Long institutionId = persistInstitution();
        Student student = persistProspecto(institutionId);
        String token = tokenWithRole("preceptor2", "PRECEPTOR");

        MockMultipartFile file = new MockMultipartFile(
                "file", "malware.exe", "application/octet-stream", "contenido".getBytes());

        mockMvc.perform(multipart("/prospects/{id}/documents", student.getId())
                        .file(file)
                        .param("type", "DNI_FRENTE")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void agregarDocumentoValidoDevuelve201YQuedaElArchivoEnDisco() throws Exception {
        Long institutionId = persistInstitution();
        Student student = persistProspecto(institutionId);
        String token = tokenWithRole("preceptor3", "PRECEPTOR");

        MockMultipartFile file = new MockMultipartFile(
                "file", "dni.pdf", "application/pdf", new byte[2 * 1024 * 1024]);

        mockMvc.perform(multipart("/prospects/{id}/documents", student.getId())
                        .file(file)
                        .param("type", "DNI_FRENTE")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("DNI_FRENTE"));

        List<StudentDocument> documents = studentDocumentRepository.findByStudentId(student.getId());
        assertThat(documents).hasSize(1);
        assertThat(Files.exists(Path.of(uploadsDirectory).resolve(documents.get(0).getFilePath()))).isTrue();
    }

    @Test
    void subirSegundoDocumentoDelMismoTipoReemplazaAlAnterior() throws Exception {
        Long institutionId = persistInstitution();
        Student student = persistProspecto(institutionId);
        String token = tokenWithRole("preceptor4", "PRECEPTOR");

        MockMultipartFile primero = new MockMultipartFile("file", "dni1.pdf", "application/pdf", "uno".getBytes());
        mockMvc.perform(multipart("/prospects/{id}/documents", student.getId())
                        .file(primero).param("type", "DNI_FRENTE").header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());

        MockMultipartFile segundo = new MockMultipartFile("file", "dni2.pdf", "application/pdf", "dos".getBytes());
        mockMvc.perform(multipart("/prospects/{id}/documents", student.getId())
                        .file(segundo).param("type", "DNI_FRENTE").header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());

        List<StudentDocument> documents = studentDocumentRepository.findByStudentId(student.getId());
        assertThat(documents).hasSize(1);
    }

    @Test
    void confirmarAltaTransicionaAConfirmadoAsignaRolYGeneraTokenDeReset() throws Exception {
        Long institutionId = persistInstitution();
        Student student = persistProspecto(institutionId);
        String token = tokenWithRole("preceptor5", "PRECEPTOR");

        ConfirmProspectRequest request = new ConfirmProspectRequest(
                "30111222", "Calle Falsa", "123", null, "Ciudad", null, null, null);

        mockMvc.perform(post("/prospects/{id}/confirm", student.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboardingStatus").value("CONFIRMADO"))
                .andExpect(jsonPath("$.dni").value("30111222"))
                .andExpect(jsonPath("$.username").value("30111222"));

        Student confirmado = studentRepository.findById(student.getId()).orElseThrow();
        assertThat(confirmado.getPerson().getRoles()).extracting(Role::getName).contains("ALUMNO");

        List<PasswordResetToken> resetTokens = passwordResetTokenRepository.findAll();
        assertThat(resetTokens).hasSize(1);

        String rawPasswordResetToken = "raw-para-test-confirmacion";
        PasswordResetToken existing = resetTokens.get(0);
        existing.setTokenHash(com.red_escuela.backend.util.TokenHashUtil.sha256Hex(rawPasswordResetToken));
        entityManager.merge(existing);
        entityManager.flush();

        authService.resetPassword(rawPasswordResetToken, "nuevaPassword123");
        AuthTokens tokens = authService.login("30111222", "nuevaPassword123", "127.0.0.1");
        assertThat(tokens.accessToken()).isNotBlank();
    }

    @Test
    void confirmarAltaDosVecesDevuelve409() throws Exception {
        Long institutionId = persistInstitution();
        Student student = persistProspecto(institutionId);
        String token = tokenWithRole("preceptor6", "PRECEPTOR");

        ConfirmProspectRequest request = new ConfirmProspectRequest(
                "30222333", "Calle Falsa", "123", null, "Ciudad", null, null, null);

        mockMvc.perform(post("/prospects/{id}/confirm", student.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/prospects/{id}/confirm", student.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void agregarDocumentoSobreStudentYaConfirmadoDevuelve201() throws Exception {
        Long institutionId = persistInstitution();
        Student student = persistProspecto(institutionId);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.merge(student);
        entityManager.flush();

        String token = tokenWithRole("preceptor7", "PRECEPTOR");
        MockMultipartFile file = new MockMultipartFile("file", "dni.pdf", "application/pdf", "contenido".getBytes());

        mockMvc.perform(multipart("/prospects/{id}/documents", student.getId())
                        .file(file).param("type", "DNI_FRENTE").header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());
    }
}
