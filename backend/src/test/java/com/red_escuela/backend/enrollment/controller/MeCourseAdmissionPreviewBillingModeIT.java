package com.red_escuela.backend.enrollment.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.support.PriceFixtures;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.platform.infrastructure.FeatureSnapshotHolder;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.testsupport.TermFixtures;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;

@SpringBootTest
@ActiveProfiles("test")
class MeCourseAdmissionPreviewBillingModeIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private TransactionTemplate transactions;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private FeatureSnapshotHolder snapshotHolder;
    @Autowired private JwtService jwtService;
    @Autowired private RoleRepository roleRepository;

    private Long courseId;
    private String studentToken;
    private String staffStudentToken;

    @BeforeEach
    void createCourseAndStudents() {
        transactions.executeWithoutResult(status -> {
            Institution institution = new Institution();
            institution.setName("Instituto " + UUID.randomUUID());
            entityManager.persist(institution);
            Person student = persistStudentPerson(institution, "ALUMNO");
            Person staffStudent = persistStudentPerson(institution, "ALUMNO", "ADMINISTRATIVO");
            studentToken = jwtService.generateAccessToken(student);
            staffStudentToken = jwtService.generateAccessToken(staffStudent);
            Term term = TermFixtures.newTerm();
            term.setYear(2026);
            term.setStatus(StatusType.ACTIVE);
            entityManager.persist(term);
            Course course = new Course();
            course.setVisibleInCatalog(true);
            course.setName("Curso " + UUID.randomUUID());
            course.setTerm(term);
            course.setCapacity(20);
            course.setIsActive(StatusType.ACTIVE);
            entityManager.persist(course);
            courseId = course.getId();
            PriceFixtures.persistPriceVersion(null, courseId, FeeType.MONTHLY_INSTALLMENT, "Precio de prueba", ars(BigDecimal.valueOf(15000)),
                    LocalDate.now().minusDays(1), student.getId());
        });
    }

    @AfterEach
    void restoreBillingFull() {
        jdbc.update("update platform_feature set enabled = true, mode = 'FULL', version = 0 where code = 'BILLING'");
        snapshotHolder.refresh();
    }

    @Test
    void billingOffHidesTheAdmissionAmountFromStudents() throws Exception {
        switchBillingTo("OFF", false);

        mockMvc.perform(preview(studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courseId").value(courseId))
                .andExpect(jsonPath("$.requiresPayment").value(false))
                .andExpect(jsonPath("$.amount").doesNotExist());
    }

    @Test
    void billingOffKeepsTheAdmissionAmountForStaff() throws Exception {
        switchBillingTo("OFF", false);

        mockMvc.perform(preview(staffStudentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiresPayment").value(true))
                .andExpect(jsonPath("$.amount.amount").value(15000.00));
    }

    @Test
    void billingMirrorKeepsTheAdmissionAmountForStudents() throws Exception {
        switchBillingTo("MIRROR", true);

        mockMvc.perform(preview(studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiresPayment").value(true))
                .andExpect(jsonPath("$.amount.amount").value(15000.00));
    }

    @Test
    void billingFullKeepsTheAdmissionAmountForStudents() throws Exception {
        mockMvc.perform(preview(studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiresPayment").value(true))
                .andExpect(jsonPath("$.amount.amount").value(15000.00));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder preview(String token) {
        return get("/me/course-enrollment-preview").param("courseId", courseId.toString()).header("Authorization", "Bearer " + token);
    }

    private void switchBillingTo(String mode, boolean enabled) {
        jdbc.update("update platform_feature set enabled = ?, mode = ? where code = 'BILLING'", enabled, mode);
        snapshotHolder.refresh();
    }

    private Person persistStudentPerson(Institution institution, String... roleNames) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        String suffix = UUID.randomUUID().toString();
        person.setName("Persona");
        person.setLastname(suffix);
        person.setDni(String.valueOf(System.nanoTime()));
        person.setUsername("preview." + suffix);
        person.setEmail("preview." + suffix + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        for (String roleName : roleNames) {
            Role role = roleRepository.findByName(roleName).orElseThrow();
            person.getRoles().add(role);
        }
        entityManager.persist(person);
        Student student = new Student();
        student.setPerson(person);
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        entityManager.flush();
        return person;
    }
}
