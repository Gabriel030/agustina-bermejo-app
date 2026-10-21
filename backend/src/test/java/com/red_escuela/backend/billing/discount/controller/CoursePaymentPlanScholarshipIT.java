package com.red_escuela.backend.billing.discount.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.entity.PaymentPlanOption;
import com.red_escuela.backend.billing.invoice.operation.IssuingScenarioFactory;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.time.MutableClock;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Tag("D-408")
@Tag("D-130")
@Tag("D-158")
@Tag("D-191")
class CoursePaymentPlanScholarshipIT extends AbstractIntegrationTest {
    private static final LocalDate TODAY = LocalDate.of(2027, 3, 10);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private IssuingScenarioFactory factory;

    private String directorToken;
    private String administrativeToken;
    private Long courseEnrollmentId;
    private Long singlePaymentOptionId;
    private Long monthlyOptionId;

    @BeforeEach
    void setUp() {
        MutableClock.INSTANCE.setDate(TODAY);
        directorToken = tokenWithRole("DIRECTOR");
        administrativeToken = tokenWithRole("ADMINISTRATIVO");
        Person director = personWithRole("DIRECTOR");
        Long studentId = factory.newStudent(factory.newProgram(Currency.ARS, 80000, 50000));
        Term term = new Term();
        term.setYear(2027);
        term.setDescription("2027-1C " + System.nanoTime());
        term.setStatus(StatusType.ACTIVE);
        term.setStartDate(LocalDateTime.of(2027, 3, 1, 0, 0));
        term.setEndDate(LocalDateTime.of(2027, 12, 31, 0, 0));
        entityManager.persist(term);
        Course course = new Course();
        course.setTerm(term);
        course.setName("Curso de pago único " + System.nanoTime());
        course.setIsActive(StatusType.ACTIVE);
        course.setCurrency(Currency.ARS);
        course.setDurationMonths(10);
        entityManager.persist(course);
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setStudent(studentRepository.findById(studentId).orElseThrow());
        enrollment.setCourse(course);
        enrollment.setStatus(EnrollmentStatus.ENROLLED);
        enrollment.setEnrollmentDate(LocalDateTime.of(2027, 3, 1, 10, 0));
        entityManager.persist(enrollment);
        courseEnrollmentId = enrollment.getId();
        singlePaymentOptionId = persistOption(course, director, PaymentPlanCadence.ANNUAL_UPFRONT, true);
        monthlyOptionId = persistOption(course, director, PaymentPlanCadence.MONTHLY, false);
        entityManager.flush();
    }

    private Long persistOption(Course course, Person createdBy, PaymentPlanCadence cadence, boolean upfrontWindow) {
        PaymentPlanOption option = new PaymentPlanOption();
        option.setCourse(course);
        option.setDescription(cadence.name());
        option.setCadence(cadence);
        option.setUpfrontWindowEnabled(upfrontWindow);
        if (upfrontWindow) {
            option.setUpfrontWindowStart(TODAY.minusDays(5));
            option.setUpfrontWindowEnd(TODAY.plusDays(20));
        }
        option.setCreatedBy(createdBy);
        entityManager.persist(option);
        return option.getId();
    }

    private Person personWithRole(String roleName) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        String username = roleName.toLowerCase() + "." + System.nanoTime();
        person.setUsername(username);
        person.setDni(Long.toString(Math.abs((long) username.hashCode())));
        person.setName("Persona");
        person.setLastname("Prueba");
        person.setEmail(username + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        person.getRoles().add(roleRepository.findByName(roleName).orElseThrow());
        entityManager.persist(person);
        entityManager.flush();
        return person;
    }

    private String tokenWithRole(String roleName) {
        return jwtService.generateAccessToken(personWithRole(roleName));
    }

    private ResultActions postJson(String path, String token, Object body) throws Exception {
        return mockMvc.perform(post(path).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions chooseSinglePayment() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("paymentPlanOptionId", singlePaymentOptionId);
        body.put("scopeMonths", 10);
        return postJson("/course-enrollments/" + courseEnrollmentId + "/payment-plan", administrativeToken, body);
    }

    private ResultActions chooseMonthlyPlan() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("paymentPlanOptionId", monthlyOptionId);
        return postJson("/course-enrollments/" + courseEnrollmentId + "/payment-plan", administrativeToken, body);
    }

    private Map<String, Object> discountBody(String type, String from, String to) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", type);
        body.put("percent", new java.math.BigDecimal("50"));
        body.put("fromPeriod", from);
        body.put("toPeriod", to);
        body.put("reason", "Situación económica");
        if ("INDIVIDUAL".equals(type)) {
            body.put("invoiceType", "MONTHLY_INSTALLMENT");
        }
        return body;
    }

    private ResultActions grantToTheCourseEnrollment(String type) throws Exception {
        return postJson("/course-enrollments/" + courseEnrollmentId + "/student-discounts", directorToken, discountBody(type, "2027-04", "2027-08"));
    }

    private JsonNode readBody(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    @Test
    void aCourseStudentWithAnActiveScholarshipCanStillChooseTheSinglePayment() throws Exception {
        grantToTheCourseEnrollment("SCHOLARSHIP").andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("ACTIVE"));

        chooseSinglePayment().andExpect(status().isCreated()).andExpect(jsonPath("$.scopeMonths").value(10));

        assertThat(jdbc.queryForObject("select count(*) from payment_plan_selection where course_enrollment_id = ? and effective_to is null", Long.class, courseEnrollmentId))
                .isEqualTo(1L);
    }

    @Test
    void theMonthlyPlanIsStillAllowedWithAScholarship() throws Exception {
        grantToTheCourseEnrollment("SCHOLARSHIP").andExpect(status().isCreated());

        chooseMonthlyPlan().andExpect(status().isCreated());
    }

    @Test
    void aScholarshipCanBeGrantedAfterTheStudentChoseTheCourseSinglePayment() throws Exception {
        chooseSinglePayment().andExpect(status().isCreated());

        JsonNode scholarship = readBody(grantToTheCourseEnrollment("SCHOLARSHIP").andExpect(status().isCreated()).andExpect(jsonPath("$.type").value("SCHOLARSHIP")));

        assertThat(scholarship.get("courseEnrollmentId").asLong()).isEqualTo(courseEnrollmentId);
        mockMvc.perform(get("/course-enrollments/" + courseEnrollmentId + "/student-discounts").header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    void anIndividualDiscountIsStillGrantableWithTheSinglePayment() throws Exception {
        chooseSinglePayment().andExpect(status().isCreated());

        grantToTheCourseEnrollment("INDIVIDUAL").andExpect(status().isCreated()).andExpect(jsonPath("$.type").value("INDIVIDUAL"));
    }
}
