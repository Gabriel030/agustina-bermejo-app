package com.red_escuela.backend.billing.discount.controller;

import com.red_escuela.backend.billing.invoice.operation.IssuingScenarioFactory;
import com.red_escuela.backend.config.AbstractIntegrationTest;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Tag("D-217")
class StudentDiscountRoleMatrixTest extends AbstractIntegrationTest {
    private static final String GRANT_BODY = """
            {"type":"SCHOLARSHIP","percent":50,"fromPeriod":"2027-04","toPeriod":"2027-08","reason":"Situación económica"}
            """;
    private static final String REVOKE_BODY = """
            {"reason":"Ya no corresponde"}
            """;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private IssuingScenarioFactory factory;

    private IssuingScenarioFactory.Learner learner;

    @BeforeEach
    void setUp() {
        MutableClock.INSTANCE.setDate(LocalDate.of(2027, 3, 10));
        learner = factory.newLearner(factory.newProgram(Currency.ARS, 80000, 50000), null);
    }

    private String tokenWithRole(String roleName) {
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
        return jwtService.generateAccessToken(person);
    }

    private String studentToken() {
        Person person = studentRepository.findById(learner.studentId()).orElseThrow().getPerson();
        person.getRoles().add(roleRepository.findByName("ALUMNO").orElseThrow());
        entityManager.flush();
        return jwtService.generateAccessToken(person);
    }

    private MockHttpServletRequestBuilder withBody(MockHttpServletRequestBuilder request, String token, String body) {
        return request.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PRECEPTOR", "DOCENTE", "ALUMNO"})
    void rolesWithoutAdministrativeAccessCannotGrantListOrRevokeStudentDiscounts(String role) throws Exception {
        String token = "ALUMNO".equals(role) ? studentToken() : tokenWithRole(role);

        mockMvc.perform(withBody(post("/program-enrollments/" + learner.programEnrollmentId() + "/student-discounts"), token, GRANT_BODY)).andExpect(status().isForbidden());
        mockMvc.perform(withBody(post("/course-enrollments/1/student-discounts"), token, GRANT_BODY)).andExpect(status().isForbidden());
        mockMvc.perform(get("/program-enrollments/" + learner.programEnrollmentId() + "/student-discounts").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/course-enrollments/1/student-discounts").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mockMvc.perform(withBody(post("/student-discounts/1/revocation"), token, REVOKE_BODY)).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PRECEPTOR", "DOCENTE", "ALUMNO"})
    void rolesWithoutAdministrativeAccessCannotUseTheGlobalListingThePerStudentListingThePreviewNorTheLostBenefitsReport(String role) throws Exception {
        String token = "ALUMNO".equals(role) ? studentToken() : tokenWithRole(role);
        Long studentId = learner.studentId();

        mockMvc.perform(get("/student-discounts").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mockMvc.perform(get("/student-discounts?studentId=" + studentId).header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mockMvc.perform(get("/students/" + studentId + "/student-discounts").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mockMvc.perform(withBody(post("/program-enrollments/" + learner.programEnrollmentId() + "/student-discounts/preview"), token, GRANT_BODY)).andExpect(status().isForbidden());
        mockMvc.perform(withBody(post("/course-enrollments/1/student-discounts/preview"), token, GRANT_BODY)).andExpect(status().isForbidden());
        mockMvc.perform(get("/reports/billing/promotions/lost-benefits").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mockMvc.perform(get("/reports/billing/promotions/lost-benefits?page=0&size=5&cutOffFrom=2027-01").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"DIRECTOR", "ADMINISTRATIVO"})
    void theStaffThatManagesDiscountsCanUseTheNewListingsAndThePreview(String role) throws Exception {
        String token = tokenWithRole(role);

        mockMvc.perform(get("/student-discounts?studentId=" + learner.studentId()).header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mockMvc.perform(get("/students/" + learner.studentId() + "/student-discounts").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mockMvc.perform(withBody(post("/program-enrollments/" + learner.programEnrollmentId() + "/student-discounts/preview"), token, GRANT_BODY)).andExpect(status().isOk());
        mockMvc.perform(get("/reports/billing/promotions/lost-benefits?page=0&size=5").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
    }

    @Test
    void aStudentCannotReachTheDiscountsOfAnotherStudentByTheStaffRoutes() throws Exception {
        mockMvc.perform(get("/students/" + (learner.studentId() + 1) + "/student-discounts").header("Authorization", "Bearer " + studentToken())).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PRECEPTOR", "DOCENTE"})
    void preceptorAndTeacherCannotReadTheStudentView(String role) throws Exception {
        mockMvc.perform(get("/me/student-discounts").header("Authorization", "Bearer " + tokenWithRole(role))).andExpect(status().isForbidden());
    }

    @Test
    void aStudentReadsTheirOwnDiscounts() throws Exception {
        mockMvc.perform(get("/me/student-discounts").header("Authorization", "Bearer " + studentToken())).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"DIRECTOR", "ADMINISTRATIVO"})
    void theStaffThatManagesDiscountsCannotUseTheStudentView(String role) throws Exception {
        mockMvc.perform(get("/me/student-discounts").header("Authorization", "Bearer " + tokenWithRole(role))).andExpect(status().isForbidden());
    }

    @Test
    void anAnonymousCallerIsRejected() throws Exception {
        mockMvc.perform(get("/me/student-discounts")).andExpect(status().isForbidden());
    }
}
