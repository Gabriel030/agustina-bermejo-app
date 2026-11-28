package com.red_escuela.backend.academicstructure.course;

import com.red_escuela.backend.testsupport.PreceptorTestFactory;
import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.CourseSubject;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
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
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CourseDurationHoursIntegrationTest extends AbstractIntegrationTest {
    @org.springframework.beans.factory.annotation.Autowired
    private PreceptorTestFactory preceptors;


    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private JwtService jwtService;
    @MockitoBean
    private GraduationDebtCheck debtCheck;

    private CycleTestFixtures fixtures;
    private String director;
    private String administrative;

    @BeforeEach
    void createUsers() {
        fixtures = new CycleTestFixtures(entityManager);
        when(debtCheck.hasDebt(anyLong())).thenReturn(false);
        director = tokenFor("DIRECTOR", 1);
        administrative = tokenFor("ADMINISTRATIVO", 2);
    }

    private String tokenFor(String roleName, int index) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setName("Horas");
        person.setLastname(roleName);
        person.setUsername("course.hours." + roleName.toLowerCase());
        person.setDni(String.valueOf(93_000_000 + index));
        person.setEmail("course.hours." + roleName.toLowerCase() + "@example.com");
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

    private Course persistCourse(Integer durationHours) {
        Term term = fixtures.term(2020);
        Course course = new Course();
        course.setName("Curso con horas");
        course.setTerm(term);
        course.setDurationHours(durationHours);
        entityManager.persist(course);
        entityManager.flush();
        return course;
    }

    private CourseEnrollment approvedEnrollmentIn(Course course) {
        Institution institution = new Institution();
        institution.setName("Instituto de prueba");
        entityManager.persist(institution);
        Student attendee = new Student();
        attendee.setPerson(fixtures.person());
        attendee.setInstitution(institution);
        attendee.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(attendee);
        CourseSubject module = new CourseSubject();
        module.setCourse(course);
        module.setName("Modulo");
        entityManager.persist(module);
        CourseSubjectSection section = new CourseSubjectSection();
        section.setName("Comision");
        section.setCourseSubject(module);
        section.setTerm(course.getTerm());
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
        moduleEnrollment.setStatus(EnrollmentStatus.APPROVED);
        entityManager.persist(moduleEnrollment);
        entityManager.flush();
        return enrollment;
    }

    private String certificateTextFor(CourseEnrollment enrollment) throws Exception {
        MvcResult result = mockMvc.perform(post("/graduation-certificates")
                        .header("Authorization", "Bearer " + director)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"COURSE\",\"enrollmentId\":" + enrollment.getId() + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        Integer id = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return pdfText(id);
    }

    private String pdfText(Integer id) throws Exception {
        MvcResult pdf = mockMvc.perform(get("/graduation-certificates/{id}/pdf", id)
                        .header("Authorization", "Bearer " + director))
                .andExpect(status().isOk())
                .andReturn();
        return textOf(pdf.getResponse().getContentAsByteArray());
    }

    private String textOf(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
        }
    }

    @Test
    void issuedCourseCertificateShowsDurationHours() throws Exception {
        CourseEnrollment enrollment = approvedEnrollmentIn(persistCourse(40));

        assertThat(certificateTextFor(enrollment)).contains("con una duración de 40 horas");
    }

    @Test
    void courseWithoutHoursOmitsDurationPhrase() throws Exception {
        CourseEnrollment enrollment = approvedEnrollmentIn(persistCourse(null));

        assertThat(certificateTextFor(enrollment)).doesNotContain("duración de");
    }

    @Test
    void courseResponseExposesAndUpdatesDurationHours() throws Exception {
        Course course = persistCourse(40);

        mockMvc.perform(get("/courses/{id}", course.getId()).header("Authorization", "Bearer " + director))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.durationHours").value(40));
        mockMvc.perform(put("/courses/{id}", course.getId()).header("Authorization", "Bearer " + administrative)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"durationHours\":60}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.durationHours").value(60));
        mockMvc.perform(put("/courses/{id}", course.getId()).header("Authorization", "Bearer " + administrative)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Otro\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.durationHours").value(60));
    }

    @Test
    void nonPositiveHoursAreRejected() throws Exception {
        Course course = persistCourse(40);
        Term term = course.getTerm();
        String createBody = "{\"name\":\"Nuevo\",\"termId\":" + term.getId()
                + ",\"capacity\":10,\"durationMonths\":3,\"durationHours\":0,\"responsiblePreceptorIds\":[" + preceptors.createPreceptorId() + "],"
                + "\"price\":{\"amount\":100,\"currency\":\"ARS\"}}";

        mockMvc.perform(post("/courses").header("Authorization", "Bearer " + administrative)
                        .contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/courses/{id}", course.getId()).header("Authorization", "Bearer " + administrative)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"durationHours\":-5}"))
                .andExpect(status().isBadRequest());
        assertThatThrownBy(() -> {
            entityManager.createNativeQuery("update course set duration_hours = 0 where id = :id")
                    .setParameter("id", course.getId()).executeUpdate();
            entityManager.flush();
        }).isInstanceOfAny(DataIntegrityViolationException.class, jakarta.persistence.PersistenceException.class);
    }
}
