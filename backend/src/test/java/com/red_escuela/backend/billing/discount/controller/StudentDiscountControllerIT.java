package com.red_escuela.backend.billing.discount.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.billing.invoice.operation.IssuingScenarioFactory;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
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
@Tag("D-217")
@Tag("D-110")
@Tag("D-238")
@Tag("D-284")
class StudentDiscountControllerIT extends AbstractIntegrationTest {
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
    private JwtService jwtService;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private IssuingScenarioFactory factory;

    private String directorToken;
    private String administrativeToken;
    private IssuingScenarioFactory.Learner learner;

    @BeforeEach
    void setUp() {
        MutableClock.INSTANCE.setDate(TODAY);
        directorToken = tokenWithRole("DIRECTOR");
        administrativeToken = tokenWithRole("ADMINISTRATIVO");
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

    private Map<String, Object> scholarshipBody(String points, String from, String to) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "SCHOLARSHIP");
        body.put("percent", new java.math.BigDecimal(points));
        body.put("fromPeriod", from);
        body.put("toPeriod", to);
        body.put("reason", "Situación económica");
        return body;
    }

    private String grantPath() {
        return "/program-enrollments/" + learner.programEnrollmentId() + "/student-discounts";
    }

    private ResultActions postJson(String path, String token, Object body) throws Exception {
        return mockMvc.perform(post(path).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    private JsonNode readBody(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    @Test
    void theDirectorGrantsAScholarshipImmediatelyAndTheAdministrativeLeavesAPendingRequest() throws Exception {
        JsonNode granted = readBody(postJson(grantPath(), directorToken, scholarshipBody("50", "2027-04", "2027-08")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE")).andExpect(jsonPath("$.type").value("SCHOLARSHIP")).andExpect(jsonPath("$.applicableInvoiceType").value("MONTHLY_INSTALLMENT")));
        assertThat(granted.get("fromPeriod").asText()).isEqualTo("2027-04");

        JsonNode pending = readBody(postJson(grantPath(), administrativeToken, scholarshipBody("25", "2027-09", "2027-12")).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.authorizationRequestId").isNumber()));

        assertThat(jdbc.queryForObject("select count(*) from student_discount where student_id = ?", Long.class, learner.studentId())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select operation_type from authorization_request where id = ?", String.class, pending.get("authorizationRequestId").asLong()))
                .isEqualTo("GRANT_STUDENT_DISCOUNT");
    }

    @Test
    void aProposalOverlappingAnActiveDiscountOfTheSameKindIsAConflictForBothRoles() throws Exception {
        postJson(grantPath(), directorToken, scholarshipBody("50", "2027-04", "2027-12")).andExpect(status().isCreated());

        postJson(grantPath(), directorToken, scholarshipBody("30", "2027-06", "2027-12")).andExpect(status().isConflict());
        postJson(grantPath(), administrativeToken, scholarshipBody("30", "2027-06", "2027-12")).andExpect(status().isConflict());

        assertThat(jdbc.queryForObject("select count(*) from student_discount where student_id = ?", Long.class, learner.studentId())).isEqualTo(1L);
    }

    @Test
    void otherRolesAndAnonymousCallersAreRejectedOnEveryEndpoint() throws Exception {
        for (String role : new String[]{"PRECEPTOR", "DOCENTE", "ALUMNO"}) {
            String token = tokenWithRole(role);
            postJson(grantPath(), token, scholarshipBody("50", "2027-04", "2027-08")).andExpect(status().isForbidden());
            postJson("/student-discounts/1/revocation", token, Map.of("reason", "x")).andExpect(status().isForbidden());
            mockMvc.perform(get(grantPath()).header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        }
        mockMvc.perform(get(grantPath())).andExpect(status().isForbidden());
    }

    @Test
    void errorsCarryTheCodesTheSpecNames() throws Exception {
        Map<String, Object> onFee = scholarshipBody("50", "2027-04", "2027-04");
        onFee.put("invoiceType", "ENROLLMENT_FEE");
        postJson(grantPath(), directorToken, onFee).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DISCOUNT_INVOICE_TYPE_MISMATCH"));

        postJson(grantPath(), directorToken, scholarshipBody("50", "2027-04", "2027-02")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SCHOLARSHIP_PERIOD"));

        Long scholarshipId = readBody(postJson(grantPath(), directorToken, scholarshipBody("50", "2027-04", "2027-08")).andExpect(status().isCreated())).get("id").asLong();
        postJson("/student-discounts/" + scholarshipId + "/revocation", directorToken, Map.of("reason", " ")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REVOCATION_REASON_REQUIRED"));
        postJson("/student-discounts/" + scholarshipId + "/revocation", directorToken, Map.of("reason", "Corresponde")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REVOKED")).andExpect(jsonPath("$.revokedFromPeriod").value("2027-03"));
        postJson("/student-discounts/" + scholarshipId + "/revocation", directorToken, Map.of("reason", "Otra vez")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ILLEGAL_STUDENT_DISCOUNT_TRANSITION"));
        postJson("/student-discounts/9999999/revocation", directorToken, Map.of("reason", "x")).andExpect(status().isNotFound());
    }

    @Test
    void theListShowsTheDiscountsOfTheEnrollmentNewestFirst() throws Exception {
        postJson(grantPath(), directorToken, scholarshipBody("50", "2027-04", "2027-08")).andExpect(status().isCreated());
        postJson(grantPath(), directorToken, scholarshipBody("20", "2027-09", "2027-12")).andExpect(status().isCreated());

        mockMvc.perform(get(grantPath()).header("Authorization", "Bearer " + administrativeToken)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[0].fromPeriod").value("2027-09"));
    }
}
