package com.red_escuela.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.billing.dto.CreateAdHocInvoiceRequest;
import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.academicstructure.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static com.red_escuela.backend.shared.domain.TestMoney.ars;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdHocInvoiceControllerTest extends AbstractIntegrationTest {
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

    private CourseEnrollment persistCourseEnrollment() {
        Institution institution = new Institution();
        institution.setName("Instituto de prueba");
        entityManager.persist(institution);

        Person studentPerson = new Person();
        studentPerson.setUsername("alumno.adhoc." + System.nanoTime());
        studentPerson.setEmail(studentPerson.getUsername() + "@example.com");
        Address address = new Address();
        entityManager.persist(address);
        studentPerson.setAddress(address);
        studentPerson.setStatus(StatusType.ACTIVE);
        entityManager.persist(studentPerson);

        Student student = new Student();
        student.setPerson(studentPerson);
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);

        Term term = new Term();
        term.setYear(2026);
        term.setStatus(StatusType.ACTIVE);
        entityManager.persist(term);

        Course course = new Course();
        course.setName("Curso de prueba " + System.nanoTime());
        course.setTerm(term);
        course.setIsActive(StatusType.ACTIVE);
        entityManager.persist(course);

        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setStudent(student);
        enrollment.setCourse(course);
        enrollment.setStatus(EnrollmentStatus.ENROLLED);
        enrollment.setEnrollmentDate(LocalDateTime.now());
        entityManager.persist(enrollment);
        entityManager.flush();
        return enrollment;
    }

    @Test
    void adHocCreationOnlyAvailableToAdministrative() throws Exception {
        CourseEnrollment enrollment = persistCourseEnrollment();
        String tokenAlumno = tokenWithRole("alumno.adhoc.test", "ALUMNO");
        String tokenPreceptor = tokenWithRole("preceptor.adhoc.test", "PRECEPTOR");

        CreateAdHocInvoiceRequest request = new CreateAdHocInvoiceRequest(
                FeeType.EXAM_RIGHT, ars(1500), LocalDate.now(), "Derecho de examen", null, null);
        String body = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/course-enrollments/{id}/invoices/ad-hoc", enrollment.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + tokenAlumno)
                        .content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/course-enrollments/{id}/invoices/ad-hoc", enrollment.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + tokenPreceptor)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void adHocCreationAsAdministrativeReturns201() throws Exception {
        CourseEnrollment enrollment = persistCourseEnrollment();
        String tokenAdministrativo = tokenWithRole("administrativo.adhoc.test", "ADMINISTRATIVO");

        CreateAdHocInvoiceRequest request = new CreateAdHocInvoiceRequest(
                FeeType.DEGREE_ISSUANCE, ars(3000), LocalDate.now(), "Emisión de título", null, null);

        mockMvc.perform(post("/course-enrollments/{id}/invoices/ad-hoc", enrollment.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + tokenAdministrativo)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }
}
