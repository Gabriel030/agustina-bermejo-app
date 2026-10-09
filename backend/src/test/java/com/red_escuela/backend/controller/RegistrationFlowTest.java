package com.red_escuela.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.dto.RegisterRequest;
import com.red_escuela.backend.enrollment.dto.RejectProgramEnrollmentApplicationRequest;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.billing.entity.CohortIntake;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.academicstructure.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.billing.entity.PriceRevision;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.billing.entity.ProgramCohort;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplicationStatus;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentApplicationRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramRepository;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import com.red_escuela.backend.billing.dto.PaymentAllocationRequest;
import com.red_escuela.backend.billing.dto.RegisterPaymentRequest;
import com.red_escuela.backend.billing.entity.PaymentMethod;
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
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import static com.red_escuela.backend.shared.domain.TestMoney.ars;

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

    @Autowired
    private com.red_escuela.backend.billing.usecase.payment.RegisterPaymentUseCase registerPaymentUseCase;

    private Long persistInstitution() {
        Institution institution = new Institution();
        institution.setName("Instituto de prueba");
        entityManager.persist(institution);
        entityManager.flush();
        return institution.getId();
    }

    private Program persistProgram() {
        Program program = new Program();
        program.setName("Diseño Gráfico " + System.nanoTime());
        program.setTotalYears(3);
        program.setTotalCreditsRequired(0);
        return programRepository.save(program);
    }

    private ProgramCohort persistCohort(Program program) {
        Address address = new Address();
        entityManager.persist(address);
        Person director = new Person();
        director.setUsername("director.cohort." + System.nanoTime());
        director.setEmail(director.getUsername() + "@example.com");
        director.setAddress(address);
        director.setStatus(StatusType.ACTIVE);
        entityManager.persist(director);

        Term term = new Term();
        term.setYear(2026);
        term.setStatus(StatusType.ACTIVE);
        entityManager.persist(term);

        ProgramYearBillingTemplate template = new ProgramYearBillingTemplate();
        template.setProgram(program);
        template.setTerm(term);
        com.red_escuela.backend.billing.planning.TestTemplateTerms.setRecurringFeeInvoicesCount(template, 10);
        template.setCreatedBy(director);
        entityManager.persist(template);
        com.red_escuela.backend.billing.planning.TestTemplateTerms.publish(entityManager, template);

        ProgramCohort cohort = new ProgramCohort();
        cohort.setTemplate(template);
        cohort.setIntake(CohortIntake.FIRST);
        cohort.setAdmissionStart(LocalDate.now().minusDays(30));
        cohort.setAdmissionEnd(LocalDate.now().plusDays(30));
        cohort.setCursadaStart(LocalDate.now());
        cohort.setCreatedBy(director);
        entityManager.persist(cohort);
        entityManager.flush();
        return cohort;
    }

    private Course persistCourse(int capacity) {
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
    private com.red_escuela.backend.identity.infrastructure.JwtService jwtService;

    private String preceptorToken() {
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

    private String administrativeToken() {
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

    private void persistPriceRevision(Program program, Course course, FeeType feeType, BigDecimal amount) {
        Address address = new Address();
        entityManager.persist(address);
        Person director = new Person();
        director.setUsername("director.fee." + System.nanoTime());
        director.setEmail(director.getUsername() + "@example.com");
        director.setAddress(address);
        director.setStatus(StatusType.ACTIVE);
        entityManager.persist(director);

        PriceRevision revision = new PriceRevision();
        revision.setProgram(program);
        revision.setCourse(course);
        revision.setFeeType(feeType);
        revision.setDescription("Precio de prueba");
        revision.setMonthlyAmount(ars(amount));
        revision.setEffectiveFrom(LocalDate.now().minusDays(1));
        revision.setCreatedBy(director);
        entityManager.persist(revision);
        entityManager.flush();
    }

    private MockMultipartFile comprobante() {
        return new MockMultipartFile("comprobante", "comprobante.pdf", "application/pdf", "contenido de prueba".getBytes());
    }

    private RegisterRequest validRegistration(String sufijo, Long institutionId) {
        return new RegisterRequest(
                "Sofía", "Ramírez", "sofia.ramirez." + sufijo + "@example.com",
                "sofia.ramirez." + sufijo, "Test1234", institutionId);
    }

    private void giveDni(String suffix) {
        entityManager.flush();
        entityManager.createQuery("update Person p set p.dni = :dni where p.username = :username")
                .setParameter("dni", "9" + Math.abs(suffix.hashCode())).setParameter("username", "sofia.ramirez." + suffix).executeUpdate();
        entityManager.clear();
    }

    @Test
    void registeringReturnsImmediatelyUsableTokens() throws Exception {
        Long institutionId = persistInstitution();

        String response = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRegistration("ok", institutionId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").exists())
                .andReturn().getResponse().getContentAsString();

        String accessToken = objectMapper.readTree(response).get("accessToken").asText();

        mockMvc.perform(get("/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Sofía"))
                .andExpect(jsonPath("$.roles").isEmpty());
    }

    @Test
    void registeringWithExistingUsernameReturns409() throws Exception {
        Long institutionId = persistInstitution();
        RegisterRequest request = validRegistration("dup", institutionId);

        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void registeringWithExistingEmailAndDifferentUsernameReturns409() throws Exception {
        Long institutionId = persistInstitution();
        RegisterRequest primero = validRegistration("email-dup-a", institutionId);
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
    void registeringWithWeakPasswordReturns400() throws Exception {
        Long institutionId = persistInstitution();
        RegisterRequest debil = new RegisterRequest(
                "Sofía", "Ramírez", "sofia.debil@example.com", "sofia.debil", "abc", institutionId);

        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(debil)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fullFlowApprovedProgramRequest() throws Exception {
        Long institutionId = persistInstitution();
        Program program = persistProgram();
        ProgramCohort cohort = persistCohort(program);
        persistPriceRevision(program, null, FeeType.ENROLLMENT_FEE, BigDecimal.valueOf(50000));

        String registerResponse = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRegistration("carrera", institutionId))))
                .andReturn().getResponse().getContentAsString();
        String accessToken = objectMapper.readTree(registerResponse).get("accessToken").asText();
        giveDni("carrera");

        String solicitudResponse = mockMvc.perform(post("/me/program-enrollment-applications")
                        .param("programId", program.getId().toString())
                        .param("cohortId", cohort.getId().toString())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        Long applicationId = objectMapper.readTree(solicitudResponse).get("id").asLong();

        mockMvc.perform(post("/me/program-enrollment-applications")
                        .param("programId", program.getId().toString())
                        .param("cohortId", cohort.getId().toString())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isConflict());

        String tokenPreceptor = preceptorToken();
        mockMvc.perform(post("/program-enrollment-applications/" + applicationId + "/approve")
                        .header("Authorization", "Bearer " + tokenPreceptor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        entityManager.flush();
        entityManager.clear();

        ProgramEnrollmentApplication persisted = applicationRepository.findById(applicationId).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(ProgramEnrollmentApplicationStatus.APPROVED);

        Student student = persisted.getStudent();
        assertThat(student.getPerson().getRoles()).extracting(Role::getName).contains("ALUMNO");
        assertThat(persisted.getAdmissionInvoice()).isNotNull();
        assertThat(programEnrollmentRepository.findByStudentIdAndProgramIdAndStatusIn(
                student.getId(), program.getId(),
                java.util.List.of(com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus.ENROLLED)))
                .isEmpty();

        registerPaymentUseCase.register(new RegisterPaymentRequest(
                java.util.List.of(new PaymentAllocationRequest(persisted.getAdmissionInvoice().getId(),
                        persisted.getAdmissionInvoice().getAmounts().outstandingAmount().value())),
                PaymentMethod.TRANSFER, false, null, null, null, null), persisted.getDecidedBy());
        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(com.red_escuela.backend.billing.invoice.domain.Invoice.class, persisted.getAdmissionInvoice().getId()).getPaymentStatus())
                .isEqualTo(com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus.PAID);
    }

    @Test
    @org.junit.jupiter.api.Tag("D-228")
    void requestingAProgramOrACourseWithoutDniIsRejectedWithDniRequired() throws Exception {
        Long institutionId = persistInstitution();
        Program program = persistProgram();
        ProgramCohort cohort = persistCohort(program);
        Course course = persistCourse(20);
        persistPriceRevision(program, null, FeeType.ENROLLMENT_FEE, BigDecimal.valueOf(50000));
        persistPriceRevision(null, course, FeeType.MONTHLY_INSTALLMENT, BigDecimal.valueOf(15000));
        String registerResponse = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRegistration("sindni", institutionId))))
                .andReturn().getResponse().getContentAsString();
        String accessToken = objectMapper.readTree(registerResponse).get("accessToken").asText();

        mockMvc.perform(post("/me/program-enrollment-applications")
                        .param("programId", program.getId().toString())
                        .param("cohortId", cohort.getId().toString())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DNI_REQUIRED"));
        mockMvc.perform(multipart("/me/course-enrollments")
                        .file(comprobante())
                        .param("courseId", course.getId().toString())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DNI_REQUIRED"));
    }

    @Test
    @org.junit.jupiter.api.Tag("D-228")
    @org.junit.jupiter.api.Tag("D-233")
    void aForeignStudentWithAPassportCanRequestAProgram() throws Exception {
        Long institutionId = persistInstitution();
        Program program = persistProgram();
        ProgramCohort cohort = persistCohort(program);
        persistPriceRevision(program, null, FeeType.ENROLLMENT_FEE, BigDecimal.valueOf(50000));
        String registerResponse = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRegistration("pasaporte", institutionId))))
                .andReturn().getResponse().getContentAsString();
        String accessToken = objectMapper.readTree(registerResponse).get("accessToken").asText();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + accessToken)
                        .content("{\"documentType\":\"PASSPORT\",\"dni\":\"X1234567\"}"))
                .andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(post("/me/program-enrollment-applications")
                        .param("programId", program.getId().toString())
                        .param("cohortId", cohort.getId().toString())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @org.junit.jupiter.api.Tag("D-228")
    @org.junit.jupiter.api.Tag("D-233")
    void aDniWithLettersIsRejectedAndLeavesTheProgramRequestWithoutDocument() throws Exception {
        Long institutionId = persistInstitution();
        Program program = persistProgram();
        ProgramCohort cohort = persistCohort(program);
        persistPriceRevision(program, null, FeeType.ENROLLMENT_FEE, BigDecimal.valueOf(50000));
        String registerResponse = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRegistration("dnibad", institutionId))))
                .andReturn().getResponse().getContentAsString();
        String accessToken = objectMapper.readTree(registerResponse).get("accessToken").asText();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + accessToken)
                        .content("{\"documentType\":\"DNI\",\"dni\":\"12AB5678\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DOCUMENT_NUMBER"));

        mockMvc.perform(post("/me/program-enrollment-applications")
                        .param("programId", program.getId().toString())
                        .param("cohortId", cohort.getId().toString())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DNI_REQUIRED"));
    }

    @Test
    void rejectingRequestCreatesNoEnrollmentAndGrantsNoRole() throws Exception {
        Long institutionId = persistInstitution();
        Program program = persistProgram();
        ProgramCohort cohort = persistCohort(program);
        persistPriceRevision(program, null, FeeType.ENROLLMENT_FEE, BigDecimal.valueOf(50000));

        String registerResponse = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRegistration("rechazo", institutionId))))
                .andReturn().getResponse().getContentAsString();
        String accessToken = objectMapper.readTree(registerResponse).get("accessToken").asText();
        giveDni("rechazo");

        String solicitudResponse = mockMvc.perform(post("/me/program-enrollment-applications")
                        .param("programId", program.getId().toString())
                        .param("cohortId", cohort.getId().toString())
                        .header("Authorization", "Bearer " + accessToken))
                .andReturn().getResponse().getContentAsString();
        Long applicationId = objectMapper.readTree(solicitudResponse).get("id").asLong();

        String tokenPreceptor = preceptorToken();
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
                java.util.List.of(com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus.ENROLLED)))
                .isEmpty();
        assertThat(student.getPerson().getRoles()).isEmpty();
    }

    @Test
    void aStudentCannotApproveTheirOwnRequest() throws Exception {
        Long institutionId = persistInstitution();
        Program program = persistProgram();
        ProgramCohort cohort = persistCohort(program);
        persistPriceRevision(program, null, FeeType.ENROLLMENT_FEE, BigDecimal.valueOf(50000));

        String registerResponse = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRegistration("selfapprove", institutionId))))
                .andReturn().getResponse().getContentAsString();
        String accessToken = objectMapper.readTree(registerResponse).get("accessToken").asText();
        giveDni("selfapprove");

        String solicitudResponse = mockMvc.perform(post("/me/program-enrollment-applications")
                        .param("programId", program.getId().toString())
                        .param("cohortId", cohort.getId().toString())
                        .header("Authorization", "Bearer " + accessToken))
                .andReturn().getResponse().getContentAsString();
        Long applicationId = objectMapper.readTree(solicitudResponse).get("id").asLong();

        mockMvc.perform(post("/program-enrollment-applications/" + applicationId + "/approve")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void enrollingInACourseStaysUnderReviewAndOnlyGrantsTheRoleOnApprovingTheReceipt() throws Exception {
        Long institutionId = persistInstitution();
        Course course = persistCourse(20);
        persistPriceRevision(null, course, FeeType.MONTHLY_INSTALLMENT, BigDecimal.valueOf(15000));

        String registerResponse = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRegistration("curso", institutionId))))
                .andReturn().getResponse().getContentAsString();
        String accessToken = objectMapper.readTree(registerResponse).get("accessToken").asText();
        giveDni("curso");

        mockMvc.perform(multipart("/me/course-enrollments")
                        .file(comprobante())
                        .param("courseId", course.getId().toString())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentStatus").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.courseEnrollmentId").doesNotExist());

        mockMvc.perform(get("/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").isEmpty());

        String tokenAdministrativo = administrativeToken();
        String pendientesResponse = mockMvc.perform(get("/bank-transfer-receipts")
                        .header("Authorization", "Bearer " + tokenAdministrativo))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode pendientes = objectMapper.readTree(pendientesResponse);
        Long receiptId = pendientes.get(pendientes.size() - 1).get("id").asLong();

        mockMvc.perform(post("/bank-transfer-receipts/" + receiptId + "/approve")
                        .header("Authorization", "Bearer " + tokenAdministrativo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(get("/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("ALUMNO"));

        String misSolicitudesResponse = mockMvc.perform(get("/me/course-enrollment-applications")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(misSolicitudesResponse).get(0).get("courseEnrollmentId").isNull()).isFalse();
    }
}
