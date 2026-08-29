package com.red_escuela.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.dto.RegisterRequest;
import com.red_escuela.backend.dto.RejectProgramEnrollmentApplicationRequest;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.entity.Course;
import com.red_escuela.backend.entity.FeeSchedule;
import com.red_escuela.backend.entity.FeeScheduleConcept;
import com.red_escuela.backend.entity.Institution;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.Program;
import com.red_escuela.backend.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.entity.ProgramEnrollmentApplicationStatus;
import com.red_escuela.backend.entity.Role;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.entity.Student;
import com.red_escuela.backend.entity.Term;
import com.red_escuela.backend.repository.CourseRepository;
import com.red_escuela.backend.repository.ProgramEnrollmentApplicationRepository;
import com.red_escuela.backend.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.repository.ProgramRepository;
import com.red_escuela.backend.repository.RoleRepository;
import com.red_escuela.backend.repository.TermRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Épica 7: registro independiente + solicitud de alta en carrera + inscripción
 * directa a curso. Integración real contra Postgres (AbstractIntegrationTest),
 * nada de mocks de repository — ver docs/context/testing.md.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RegistrationFlowTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private ProgramRepository programRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private TermRepository termRepository;

    @Autowired
    private ProgramEnrollmentApplicationRepository applicationRepository;

    @Autowired
    private ProgramEnrollmentRepository programEnrollmentRepository;

    private Long persistInstitution() {
        Institution institution = new Institution();
        institution.setName("Instituto de prueba");
        entityManager.persist(institution);
        entityManager.flush();
        return institution.getId();
    }

    private Program persistPrograma() {
        Program program = new Program();
        program.setName("Diseño Gráfico " + System.nanoTime());
        program.setTotalYears(3);
        program.setTotalCreditsRequired(0);
        return programRepository.save(program);
    }

    private Course persistCurso(int capacity) {
        Term term = new Term();
        term.setYear(2026);
        term.setStatus(StatusType.ACTIVE);
        term = termRepository.save(term);

        Course course = new Course();
        course.setName("Illustrator desde cero " + System.nanoTime());
        course.setTerm(term);
        course.setCapacity(capacity);
        course.setIsActive(StatusType.ACTIVE);
        return courseRepository.save(course);
    }

    @Autowired
    private com.red_escuela.backend.service.JwtService jwtService;

    private String tokenDePreceptor() {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setUsername("preceptor.flujo." + System.nanoTime());
        person.setEmail(person.getUsername() + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        Role role = roleRepository.findByName("PRECEPTOR").orElseThrow();
        person.getRoles().add(role);
        entityManager.persist(person);
        entityManager.flush();
        return jwtService.generateAccessToken(person);
    }

    private String tokenDeAdministrativo() {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setUsername("administrativo.flujo." + System.nanoTime());
        person.setEmail(person.getUsername() + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        Role role = roleRepository.findByName("ADMINISTRATIVO").orElseThrow();
        person.getRoles().add(role);
        entityManager.persist(person);
        entityManager.flush();
        return jwtService.generateAccessToken(person);
    }

    // Épica 7 Fase 3: matrícula/cuota salen de FeeSchedule (Épica 8 Fase 1), no de
    // Program.price/Course.price — sin esto, crearDesdeFeeSchedule() falla con
    // FeeScheduleNotFoundException.
    private void persistFeeSchedule(Program program, Course course, FeeScheduleConcept concept, BigDecimal amount) {
        Address address = new Address();
        entityManager.persist(address);
        Person director = new Person();
        director.setUsername("director.fee." + System.nanoTime());
        director.setEmail(director.getUsername() + "@example.com");
        director.setAddress(address);
        director.setStatus(StatusType.ACTIVE);
        entityManager.persist(director);

        FeeSchedule feeSchedule = new FeeSchedule();
        feeSchedule.setProgram(program);
        feeSchedule.setCourse(course);
        feeSchedule.setConcept(concept);
        feeSchedule.setAmount(amount);
        feeSchedule.setVigenteDesde(LocalDate.now().minusDays(1));
        feeSchedule.setCreatedBy(director);
        feeSchedule.setCreatedAt(LocalDateTime.now());
        entityManager.persist(feeSchedule);
        entityManager.flush();
    }

    private MockMultipartFile comprobante() {
        return new MockMultipartFile("comprobante", "comprobante.pdf", "application/pdf", "contenido de prueba".getBytes());
    }

    private RegisterRequest registroValido(String sufijo, Long institutionId) {
        return new RegisterRequest(
                "Sofía", "Ramírez", "sofia.ramirez." + sufijo + "@example.com",
                "sofia.ramirez." + sufijo, "Test1234", institutionId);
    }

    @Test
    void registrarseDevuelveTokensYaUtilizablesInmediatamente() throws Exception {
        Long institutionId = persistInstitution();

        String response = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registroValido("ok", institutionId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").exists())
                .andReturn().getResponse().getContentAsString();

        String accessToken = objectMapper.readTree(response).get("accessToken").asText();

        // Las credenciales elegidas en el registro ya sirven para pegarle a un
        // endpoint protegido de inmediato, sin paso de confirmación intermedio.
        mockMvc.perform(get("/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Sofía"))
                .andExpect(jsonPath("$.roles").isEmpty());
    }

    @Test
    void registrarseConUsernameYaExistenteDevuelve409() throws Exception {
        Long institutionId = persistInstitution();
        RegisterRequest request = registroValido("dup", institutionId);

        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    // Regresión (encontrado probando con qa/bruno, no por este test): a diferencia del
    // caso de arriba, acá el username es distinto — la única colisión es el email. El
    // servicio no valida esto de forma explícita (solo chequea username), pero el
    // constraint uk_person_email a nivel DB sí lo rechaza. Antes del fix, esto escapaba
    // sin manejar como DataIntegrityViolationException (Person.id es IDENTITY, el
    // insert se ejecuta antes de llegar al try/catch de RegistrationService) y, por un
    // bug separado y sistémico (/error no era permitAll), el 500 real quedaba
    // enmascarado como un 403 vacío y engañoso.
    @Test
    void registrarseConEmailYaExistenteYUsernameDistintoDevuelve409() throws Exception {
        Long institutionId = persistInstitution();
        RegisterRequest primero = registroValido("email-dup-a", institutionId);
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(primero)))
                .andExpect(status().isCreated());

        RegisterRequest segundoConMismoEmail = new RegisterRequest(
                "Otra", "Persona", primero.email(), "username-distinto-email-dup-b", "Test1234", institutionId);

        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(segundoConMismoEmail)))
                .andExpect(status().isConflict());
    }

    @Test
    void registrarseConPasswordDebilDevuelve400() throws Exception {
        Long institutionId = persistInstitution();
        RegisterRequest debil = new RegisterRequest(
                "Sofía", "Ramírez", "sofia.debil@example.com", "sofia.debil", "abc", institutionId);

        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(debil)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void flujoCompletoSolicitudDeCarreraAprobada() throws Exception {
        Long institutionId = persistInstitution();
        Program program = persistPrograma();
        persistFeeSchedule(program, null, FeeScheduleConcept.MATRICULA, BigDecimal.valueOf(50000));

        String registerResponse = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registroValido("carrera", institutionId))))
                .andReturn().getResponse().getContentAsString();
        String accessToken = objectMapper.readTree(registerResponse).get("accessToken").asText();

        String solicitudResponse = mockMvc.perform(multipart("/me/program-enrollment-applications")
                        .file(comprobante())
                        .param("programId", program.getId().toString())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        Long applicationId = objectMapper.readTree(solicitudResponse).get("id").asLong();

        // Segunda solicitud a la misma carrera mientras la primera sigue PENDING -> 409.
        mockMvc.perform(multipart("/me/program-enrollment-applications")
                        .file(comprobante())
                        .param("programId", program.getId().toString())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isConflict());

        String tokenPreceptor = tokenDePreceptor();
        mockMvc.perform(post("/program-enrollment-applications/" + applicationId + "/approve")
                        .header("Authorization", "Bearer " + tokenPreceptor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        entityManager.flush();
        entityManager.clear();

        ProgramEnrollmentApplication persisted = applicationRepository.findById(applicationId).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(ProgramEnrollmentApplicationStatus.APPROVED);

        Student student = persisted.getStudent();
        assertThat(programEnrollmentRepository.findByStudentIdAndProgramIdAndStatusIn(
                student.getId(), program.getId(),
                java.util.List.of(com.red_escuela.backend.entity.ProgramEnrollmentStatus.ENROLLED)))
                .isNotEmpty();
        assertThat(student.getPerson().getRoles()).extracting(Role::getName).contains("ALUMNO");
        // Aprobar la solicitud aprueba también el comprobante en la misma transacción
        // (ver ProgramEnrollmentApplicationService.aprobar) — el Payment real ya existe.
        assertThat(persisted.getPaymentReceipt()).isNotNull();
        assertThat(persisted.getPaymentReceipt().getStatus())
                .isEqualTo(com.red_escuela.backend.entity.PaymentReceiptStatus.APPROVED);
    }

    @Test
    void rechazarSolicitudNoCreaMatriculaNiOtorgaRol() throws Exception {
        Long institutionId = persistInstitution();
        Program program = persistPrograma();
        persistFeeSchedule(program, null, FeeScheduleConcept.MATRICULA, BigDecimal.valueOf(50000));

        String registerResponse = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registroValido("rechazo", institutionId))))
                .andReturn().getResponse().getContentAsString();
        String accessToken = objectMapper.readTree(registerResponse).get("accessToken").asText();

        String solicitudResponse = mockMvc.perform(multipart("/me/program-enrollment-applications")
                        .file(comprobante())
                        .param("programId", program.getId().toString())
                        .header("Authorization", "Bearer " + accessToken))
                .andReturn().getResponse().getContentAsString();
        Long applicationId = objectMapper.readTree(solicitudResponse).get("id").asLong();

        String tokenPreceptor = tokenDePreceptor();
        mockMvc.perform(post("/program-enrollment-applications/" + applicationId + "/reject")
                        .header("Authorization", "Bearer " + tokenPreceptor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RejectProgramEnrollmentApplicationRequest("Documentación incompleta"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Documentación incompleta"));

        entityManager.flush();
        entityManager.clear();

        ProgramEnrollmentApplication persisted = applicationRepository.findById(applicationId).orElseThrow();
        Student student = persisted.getStudent();
        assertThat(programEnrollmentRepository.findByStudentIdAndProgramIdAndStatusIn(
                student.getId(), program.getId(),
                java.util.List.of(com.red_escuela.backend.entity.ProgramEnrollmentStatus.ENROLLED)))
                .isEmpty();
        assertThat(student.getPerson().getRoles()).isEmpty();
    }

    @Test
    void unAlumnoNoPuedeAprobarSuPropiaSolicitud() throws Exception {
        Long institutionId = persistInstitution();
        Program program = persistPrograma();
        persistFeeSchedule(program, null, FeeScheduleConcept.MATRICULA, BigDecimal.valueOf(50000));

        String registerResponse = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registroValido("selfapprove", institutionId))))
                .andReturn().getResponse().getContentAsString();
        String accessToken = objectMapper.readTree(registerResponse).get("accessToken").asText();

        String solicitudResponse = mockMvc.perform(multipart("/me/program-enrollment-applications")
                        .file(comprobante())
                        .param("programId", program.getId().toString())
                        .header("Authorization", "Bearer " + accessToken))
                .andReturn().getResponse().getContentAsString();
        Long applicationId = objectMapper.readTree(solicitudResponse).get("id").asLong();

        mockMvc.perform(post("/program-enrollment-applications/" + applicationId + "/approve")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().is4xxClientError());
    }

    // Épica 7 Fase 3: ya no otorga el rol en el momento — el pago de la primera
    // cuota es un paso bloqueante, la matriculación real (y el rol) recién ocurren
    // cuando Administración aprueba el comprobante (PaymentReceiptService.aprobar).
    @Test
    void inscribirmeAUnCursoQuedaEnRevisionYRecienOtorgaElRolAlAprobarElComprobante() throws Exception {
        Long institutionId = persistInstitution();
        Course course = persistCurso(20);
        persistFeeSchedule(null, course, FeeScheduleConcept.CUOTA_MENSUAL, BigDecimal.valueOf(15000));

        String registerResponse = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registroValido("curso", institutionId))))
                .andReturn().getResponse().getContentAsString();
        String accessToken = objectMapper.readTree(registerResponse).get("accessToken").asText();

        mockMvc.perform(multipart("/me/course-enrollments")
                        .file(comprobante())
                        .param("courseId", course.getId().toString())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentStatus").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.courseEnrollmentId").doesNotExist());

        // Sin acceso todavía: ni rol ALUMNO ni CourseEnrollment real.
        mockMvc.perform(get("/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").isEmpty());

        String tokenAdministrativo = tokenDeAdministrativo();
        String pendientesResponse = mockMvc.perform(get("/payment-receipts")
                        .header("Authorization", "Bearer " + tokenAdministrativo))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long receiptId = objectMapper.readTree(pendientesResponse).get(0).get("id").asLong();

        mockMvc.perform(post("/payment-receipts/" + receiptId + "/approve")
                        .header("Authorization", "Bearer " + tokenAdministrativo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // Recién ahora: rol ALUMNO otorgado y CourseEnrollment real creado.
        mockMvc.perform(get("/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("ALUMNO"));

        String misSolicitudesResponse = mockMvc.perform(get("/me/course-enrollment-applications")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(misSolicitudesResponse).get(0).get("courseEnrollmentId").isNull()).isFalse();
    }

    // No hay test acá de "curso sin cupo" — el chequeo de capacidad real vive en
    // CourseSubjectSection, no en Course (verificado en CourseEnrollmentService),
    // y armar ese fixture completo (CourseSubject + CourseSubjectSection) es
    // deuda de testing preexistente de CourseEnrollmentService (ya sin cobertura
    // hoy, ver `find src/test -iname "*Course*"`), no algo que introduce esta fase.
    // Lo que sí es responsabilidad de esta fase — que el endpoint nuevo delegue
    // la validación sin bypassearla — está cubierto por el happy path de arriba.
}
