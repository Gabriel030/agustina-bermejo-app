package com.red_escuela.backend.graduation.certificate.controller;

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
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GraduationCertificateStaffHttpIT extends AbstractIntegrationTest {

    private static final String BASE = "/graduation-certificates";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private GraduationCertificateRepository repository;
    @MockitoBean
    private GraduationDebtCheck debtCheck;

    private CycleTestFixtures fixtures;
    private String director;
    private String administrative;
    private String student;
    private String teacher;

    @BeforeEach
    void createUsers() {
        fixtures = new CycleTestFixtures(entityManager);
        director = tokenFor("DIRECTOR", 1);
        administrative = tokenFor("ADMINISTRATIVO", 2);
        student = tokenFor("ALUMNO", 3);
        teacher = tokenFor("DOCENTE", 4);
    }

    private String tokenFor(String roleName, int index) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setName("Egreso");
        person.setLastname(roleName);
        person.setUsername("graduation.staff." + roleName.toLowerCase());
        person.setDni(String.valueOf(92_000_000 + index));
        person.setEmail("graduation.staff." + roleName.toLowerCase() + "@example.com");
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

    private ProgramEnrollment completedEnrollment() {
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

    private ResultActions send(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private ResultActions postJson(String path, String token, String body) throws Exception {
        return send(post(path).contentType(MediaType.APPLICATION_JSON).content(body), token);
    }

    private String itemJson(Long enrollmentId) {
        return "{\"kind\":\"PROGRAM\",\"enrollmentId\":" + enrollmentId + "}";
    }

    private String batchJson(Long... enrollmentIds) {
        return "{\"items\":[" + java.util.Arrays.stream(enrollmentIds).map(this::itemJson).collect(Collectors.joining(",")) + "]}";
    }

    private String issueAndReadCode(ProgramEnrollment enrollment) throws Exception {
        MvcResult result = postJson(BASE, director, itemJson(enrollment.getId())).andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.code");
    }

    private String textOf(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    @Test
    void previewReturnsWatermarkedPdfAndPersistsNothing() throws Exception {
        ProgramEnrollment enrollment = completedEnrollment();
        long before = repository.count();

        MvcResult result = postJson(BASE + "/preview", administrative, itemJson(enrollment.getId()))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_PDF))
                .andReturn();

        assertThat(textOf(result.getResponse().getContentAsByteArray())).contains("VISTA");
        assertThat(repository.count()).isEqualTo(before);
    }

    @Test
    void previewOfNotEligibleEnrollmentIsRejected() throws Exception {
        ProgramEnrollment enrollment = completedEnrollment();
        enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        entityManager.flush();

        postJson(BASE + "/preview", director, itemJson(enrollment.getId())).andExpect(status().isConflict());
    }

    @Test
    void batchIssuesEligibleItemsAndReportsTheFailedOne() throws Exception {
        ProgramEnrollment first = completedEnrollment();
        ProgramEnrollment indebted = completedEnrollment();
        ProgramEnrollment third = completedEnrollment();
        when(debtCheck.hasDebt(anyLong())).thenReturn(false);
        when(debtCheck.hasDebt(indebted.getStudent().getId())).thenReturn(true);

        postJson(BASE + "/batch", administrative, batchJson(first.getId(), indebted.getId(), third.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requested").value(3))
                .andExpect(jsonPath("$.issued").value(2))
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.results[0].outcome").value("ISSUED"))
                .andExpect(jsonPath("$.results[0].certificate.status").value("VALID"))
                .andExpect(jsonPath("$.results[0].certificate.holderDocumentNumber").isNotEmpty())
                .andExpect(jsonPath("$.results[1].outcome").value("FAILED"))
                .andExpect(jsonPath("$.results[1].errorCode").value("GRADUATION_CERTIFICATE_NOT_ELIGIBLE"))
                .andExpect(jsonPath("$.results[1].certificate").doesNotExist())
                .andExpect(jsonPath("$.results[2].outcome").value("ISSUED"));
        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void batchOverTheLimitIsRejected() throws Exception {
        String items = IntStream.rangeClosed(1, 51).mapToObj(i -> itemJson((long) i)).collect(Collectors.joining(","));

        postJson(BASE + "/batch", director, "{\"items\":[" + items + "]}").andExpect(status().isBadRequest());
    }

    @Test
    void emptyBatchIsRejected() throws Exception {
        postJson(BASE + "/batch", director, "{\"items\":[]}").andExpect(status().isBadRequest());
    }

    @Test
    void listFiltersByStatusKindAndQuery() throws Exception {
        ProgramEnrollment enrollment = completedEnrollment();
        String code = issueAndReadCode(enrollment);
        String documentNumber = enrollment.getStudent().getPerson().getDni();

        send(get(BASE).param("status", "VALID").param("kind", "PROGRAM").param("query", documentNumber), administrative)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].code").value(code))
                .andExpect(jsonPath("$.content[0].holderDocumentNumber").value(documentNumber))
                .andExpect(jsonPath("$.content[0].registryNumber").doesNotExist());
        send(get(BASE).param("status", "REVOKED"), director)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
        send(get(BASE).param("kind", "COURSE").param("query", documentNumber), director)
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void getReturnsDetailAndUnknownIdIsNotFound() throws Exception {
        ProgramEnrollment enrollment = completedEnrollment();
        String code = issueAndReadCode(enrollment);
        Long id = repository.findByCode(code).orElseThrow().getId();

        send(get(BASE + "/" + id), director)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.verificationUrl").value(org.hamcrest.Matchers.endsWith("/verificar/" + code)))
                .andExpect(jsonPath("$.status").value("VALID"));
        send(get(BASE + "/999999999"), director)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GRADUATION_CERTIFICATE_NOT_FOUND"));
    }

    @Test
    void studentAndTeacherCannotUseStaffEndpoints() throws Exception {
        for (String token : new String[] {student, teacher}) {
            send(get(BASE), token).andExpect(status().isForbidden());
            send(get(BASE + "/1"), token).andExpect(status().isForbidden());
            send(get(BASE + "/eligible"), token).andExpect(status().isForbidden());
            postJson(BASE + "/preview", token, itemJson(1L)).andExpect(status().isForbidden());
            postJson(BASE + "/batch", token, batchJson(1L)).andExpect(status().isForbidden());
        }
    }
}
