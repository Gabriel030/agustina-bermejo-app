package com.red_escuela.backend.billing.discount.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.operation.IssuingScenarioFactory;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Percentage;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Tag("D-240")
class PromotionControllerIT extends AbstractIntegrationTest {
    private static final LocalDate TODAY = LocalDate.of(2031, 3, 10);

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

    @BeforeEach
    void setUp() {
        MutableClock.INSTANCE.setDate(TODAY);
        directorToken = tokenWithRole("DIRECTOR");
        administrativeToken = tokenWithRole("ADMINISTRATIVO");
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

    private String twoBenefitPromotion(String description) throws Exception {
        Map<String, Object> monthly = new LinkedHashMap<>();
        monthly.put("feeType", "MONTHLY_INSTALLMENT");
        monthly.put("value", Map.of("type", "PROPORTIONAL", "ratePercent", new BigDecimal("30")));
        monthly.put("invoiceScopeCount", 3);
        Map<String, Object> enrollment = new LinkedHashMap<>();
        enrollment.put("feeType", "ENROLLMENT_FEE");
        enrollment.put("value", Map.of("type", "PROPORTIONAL", "ratePercent", new BigDecimal("10")));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("description", description);
        body.put("eligibilityValidFrom", "2031-03-01");
        body.put("eligibilityValidTo", "2031-04-30");
        body.put("benefits", List.of(monthly, enrollment));
        return objectMapper.writeValueAsString(body);
    }

    @Test
    void creatingAPromotionOfAProgramKeepsTheContractAndCreatesOneCohortPromotionPerBenefit() throws Exception {
        var offering = factory.newProgram(Currency.ARS, 80000, 50000);

        mockMvc.perform(post("/programs/{id}/promotions", offering.programId()).header("Authorization", "Bearer " + directorToken)
                        .contentType(MediaType.APPLICATION_JSON).content(twoBenefitPromotion("Promo marzo")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value("Promo marzo"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.eligibilityValidFrom").value("2031-03-01"))
                .andExpect(jsonPath("$.eligibilityValidTo").value("2031-04-30"))
                .andExpect(jsonPath("$.createdByName").value("Persona Prueba"))
                .andExpect(jsonPath("$.benefits", hasSize(2)))
                .andExpect(jsonPath("$.benefits[0].feeType").value("MONTHLY_INSTALLMENT"))
                .andExpect(jsonPath("$.benefits[0].invoiceScopeCount").value(3))
                .andExpect(jsonPath("$.benefits[0].value.type").value("PROPORTIONAL"))
                .andExpect(jsonPath("$.benefits[1].feeType").value("ENROLLMENT_FEE"));

        assertThat(jdbc.queryForObject("select count(*) from discount_definition where discount_type = 'COHORT_PROMOTION' and name = 'Promo marzo' and program_id = ? and status = 'ACTIVE'",
                Long.class, offering.programId())).isEqualTo(2L);
    }

    @Test
    void listingShowsTheActivePromotionsAsOnePromotionWithItsBenefitsAndClosingClosesAllOfThem() throws Exception {
        var offering = factory.newProgram(Currency.ARS, 80000, 50000);
        MvcResult created = mockMvc.perform(post("/programs/{id}/promotions", offering.programId()).header("Authorization", "Bearer " + directorToken)
                .contentType(MediaType.APPLICATION_JSON).content(twoBenefitPromotion("Promo abril"))).andExpect(status().isCreated()).andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/promotions").header("Authorization", "Bearer " + administrativeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.description == 'Promo abril')].benefits.length()").value(2));

        mockMvc.perform(post("/promotions/{id}/close", id).header("Authorization", "Bearer " + directorToken)).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("select count(*) from discount_definition where name = 'Promo abril' and status = 'CLOSED'", Long.class)).isEqualTo(2L);
        mockMvc.perform(get("/promotions").header("Authorization", "Bearer " + administrativeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.description == 'Promo abril')]").isEmpty());
    }

    @Test
    void anAdministrativeUserCannotCreateOrClosePromotions() throws Exception {
        var offering = factory.newProgram(Currency.ARS, 80000, 50000);

        mockMvc.perform(post("/programs/{id}/promotions", offering.programId()).header("Authorization", "Bearer " + administrativeToken)
                .contentType(MediaType.APPLICATION_JSON).content(twoBenefitPromotion("Promo"))).andExpect(status().isForbidden());
        mockMvc.perform(post("/promotions/{id}/close", 1L).header("Authorization", "Bearer " + administrativeToken)).andExpect(status().isForbidden());
    }

    @Test
    void twoBenefitsOfTheSameFeeTypeAreRejected() throws Exception {
        var offering = factory.newProgram(Currency.ARS, 80000, 50000);
        Map<String, Object> benefit = Map.of("feeType", "ENROLLMENT_FEE", "value", Map.of("type", "PROPORTIONAL", "ratePercent", new BigDecimal("10")));
        String body = objectMapper.writeValueAsString(Map.of("description", "Promo", "eligibilityValidFrom", "2031-03-01", "eligibilityValidTo", "2031-04-30",
                "benefits", List.of(benefit, benefit)));

        mockMvc.perform(post("/programs/{id}/promotions", offering.programId()).header("Authorization", "Bearer " + directorToken)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
    }

    @Test
    @Tag("D-283")
    @Tag("D-074")
    void theLostBenefitsReportListsTheInstallmentsNotUsedWhenTheAvailableUntilDatePasses() throws Exception {
        var offering = factory.newProgram(Currency.ARS, 80000, 50000);
        var learner = factory.newLearner(offering, null);
        Long promotionId = factory.acquireCohortPromotion(offering, learner, FeeType.MONTHLY_INSTALLMENT, Percentage.ofPoints(new BigDecimal("30")), 3, LocalDate.of(2031, 2, 28));
        entityManager.flush();

        mockMvc.perform(get("/reports/billing/promotions/lost-benefits").header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.promotionRedemptionId == " + promotionId + ")].used").value(0))
                .andExpect(jsonPath("$[?(@.promotionRedemptionId == " + promotionId + ")].scopeCount").value(3))
                .andExpect(jsonPath("$[?(@.promotionRedemptionId == " + promotionId + ")].remaining").value(3))
                .andExpect(jsonPath("$[?(@.promotionRedemptionId == " + promotionId + ")].feeType").value("MONTHLY_INSTALLMENT"))
                .andExpect(jsonPath("$[?(@.promotionRedemptionId == " + promotionId + ")].cutOffAt").value("2031-02"));
    }

    @Test
    @Tag("D-283")
    void aPromotionStillAvailableIsNotInTheLostBenefitsReport() throws Exception {
        var offering = factory.newProgram(Currency.ARS, 80000, 50000);
        var learner = factory.newLearner(offering, null);
        Long promotionId = factory.acquireCohortPromotion(offering, learner, FeeType.MONTHLY_INSTALLMENT, Percentage.ofPoints(new BigDecimal("30")), 3, LocalDate.of(2031, 3, 10));
        entityManager.flush();

        mockMvc.perform(get("/reports/billing/promotions/lost-benefits").header("Authorization", "Bearer " + administrativeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.promotionRedemptionId == " + promotionId + ")]").isEmpty());
    }
}
