package com.red_escuela.backend.graduation.certificate.controller;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.CourseSubject;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
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

    private CourseEnrollment courseEnrollment(EnrollmentStatus moduleStatus) {
        Institution institution = new Institution();
        institution.setName("Instituto de prueba");
        entityManager.persist(institution);
        Student attendee = new Student();
        attendee.setPerson(fixtures.person());
        attendee.setInstitution(institution);
        attendee.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(attendee);
        Term term = fixtures.term(2020);
        Course course = new Course();
        course.setName("Curso de egreso");
        course.setTerm(term);
        entityManager.persist(course);
        CourseSubject module = new CourseSubject();
        module.setCourse(course);
        module.setName("Modulo");
        entityManager.persist(module);
        CourseSubjectSection section = new CourseSubjectSection();
        section.setName("Comision");
        section.setCourseSubject(module);
        section.setTerm(term);
        section.setStatus(StatusType.ACTIVE);
        entityManager.persist(section);
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setStudent(attendee);
        enrollment.setCourse(course);
        enrollment.setStatus(EnrollmentStatus.IN_PROGRESS);
        enrollment.setEnrollmentDate(LocalDateTime.now().minusYears(7));
        entityManager.persist(enrollment);
        CourseSubjectSectionEnrollment moduleEnrollment = new CourseSubjectSectionEnrollment();
        moduleEnrollment.setStudent(attendee);
        moduleEnrollment.setCourseEnrollment(enrollment);
        moduleEnrollment.setCourseSubjectSection(section);
        moduleEnrollment.setStatus(moduleStatus);
        entityManager.persist(moduleEnrollment);
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
                .andExpect(jsonPath("$.results[1].errorCode").value("GRADUATION_CERTIFICATE_BLOCKED_BY_DEBT"))
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

    @Test
    void eligibleListsDebtorAsBlockedAndExcludesAlreadyIssued() throws Exception {
        ProgramEnrollment pending = completedEnrollment();
        ProgramEnrollment indebted = completedEnrollment();
        ProgramEnrollment issued = completedEnrollment();
        when(debtCheck.hasDebt(anyLong())).thenReturn(false);
        when(debtCheck.hasDebt(indebted.getStudent().getId())).thenReturn(true);
        issueAndReadCode(issued);
        Long studentOfPending = pending.getStudent().getId();

        MvcResult result = send(get(BASE + "/eligible").param("kind", "PROGRAM").param("size", "100"), administrative)
                .andExpect(status().isOk()).andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(JsonPath.<java.util.List<Integer>>read(body, "$.content[?(@.enrollmentId==" + issued.getId() + ")].enrollmentId")).isEmpty();
        assertThat(JsonPath.<java.util.List<String>>read(body, "$.content[?(@.enrollmentId==" + indebted.getId() + ")].status"))
                .containsExactly("BLOCKED_BY_DEBT");
        assertThat(JsonPath.<java.util.List<String>>read(body, "$.content[?(@.studentId==" + studentOfPending + ")].status"))
                .containsExactly("PENDING");
        assertThat(JsonPath.<java.util.List<String>>read(body, "$.content[?(@.studentId==" + studentOfPending + ")].holderDocumentType"))
                .containsExactly("DNI");
    }

    @Test
    void eligibleFiltersByStatusAndQuery() throws Exception {
        ProgramEnrollment pending = completedEnrollment();
        ProgramEnrollment indebted = completedEnrollment();
        when(debtCheck.hasDebt(anyLong())).thenReturn(false);
        when(debtCheck.hasDebt(indebted.getStudent().getId())).thenReturn(true);

        send(get(BASE + "/eligible").param("status", "BLOCKED_BY_DEBT").param("query", indebted.getStudent().getPerson().getDni()), director)
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].enrollmentId").value(indebted.getId()));
        send(get(BASE + "/eligible").param("status", "PENDING").param("query", indebted.getStudent().getPerson().getDni()), director)
                .andExpect(jsonPath("$.content.length()").value(0));
        send(get(BASE + "/eligible").param("status", "PENDING").param("query", pending.getStudent().getPerson().getDni().toLowerCase()), director)
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void eligibleExcludesCourseWithUnapprovedModuleAndListsApprovedOne() throws Exception {
        CourseEnrollment approved = courseEnrollment(EnrollmentStatus.APPROVED);
        CourseEnrollment failed = courseEnrollment(EnrollmentStatus.FAILED);
        when(debtCheck.hasDebt(anyLong())).thenReturn(false);

        MvcResult result = send(get(BASE + "/eligible").param("kind", "COURSE").param("size", "100"), director)
                .andExpect(status().isOk()).andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(JsonPath.<java.util.List<Integer>>read(body, "$.content[*].enrollmentId"))
                .contains(approved.getId().intValue()).doesNotContain(failed.getId().intValue());
        assertThat(JsonPath.<java.util.List<String>>read(body, "$.content[?(@.enrollmentId==" + approved.getId() + ")].kind"))
                .containsExactly("COURSE");
    }

    @Test
    void eligiblePaginationIsStableOverSixtyOneCandidates() throws Exception {
        when(debtCheck.hasDebt(anyLong())).thenReturn(false);
        java.util.Set<Integer> expected = new java.util.HashSet<>();
        for (int i = 0; i < 61; i++) {
            expected.add(completedEnrollment().getId().intValue());
        }
        java.util.List<Integer> collected = new java.util.ArrayList<>();
        for (int page = 0; page < 3; page++) {
            MvcResult result = send(get(BASE + "/eligible").param("kind", "PROGRAM").param("size", "25").param("page", String.valueOf(page)), director)
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(61)).andReturn();
            collected.addAll(JsonPath.read(result.getResponse().getContentAsString(), "$.content[*].enrollmentId"));
        }

        assertThat(collected).hasSize(61).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(expected);
    }
}
