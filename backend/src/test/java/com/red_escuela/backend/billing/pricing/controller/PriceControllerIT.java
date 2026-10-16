package com.red_escuela.backend.billing.pricing.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.pricing.service.PriceReader;
import com.red_escuela.backend.billing.pricing.service.PriceVersionActivator;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.pricing.OfferingReference;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PriceControllerIT extends AbstractIntegrationTest {
    private static final LocalDate TODAY = LocalDate.of(2027, 8, 20);

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
    private PriceReader priceReader;
    @Autowired
    private PriceVersionActivator activator;

    private String directorToken;
    private long programId;
    private long priceId;

    @BeforeEach
    void setUp() throws Exception {
        MutableClock.INSTANCE.setDate(TODAY);
        directorToken = tokenWithRole("DIRECTOR");
        String administrativeToken = tokenWithRole("ADMINISTRATIVO");
        String response = mockMvc.perform(post("/programs").header("Authorization", "Bearer " + administrativeToken).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Carrera de precios " + System.nanoTime(), "totalYears", 3, "totalCreditsRequired", 100,
                                "price", Map.of("amount", 100000, "currency", "ARS")))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        programId = objectMapper.readTree(response).get("id").asLong();
        priceId = jdbc.queryForObject("select id from price where program_id = ? and fee_type = 'MONTHLY_INSTALLMENT'", Long.class, programId);
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

    private static String body(long amount, String currency, LocalDate effectiveFrom, String reason, String discountJson) {
        return "{\"amount\":{\"amount\":" + amount + ",\"currency\":\"" + currency + "\"},\"effectiveFrom\":\"" + effectiveFrom + "\","
                + "\"changeReason\":" + (reason == null ? "null" : "\"" + reason + "\"") + (discountJson == null ? "" : ",\"marketingDiscount\":" + discountJson) + "}";
    }

    private ResultActions publish(String token, long amount, LocalDate effectiveFrom) throws Exception {
        return postJson("/prices/{priceId}/versions", token, body(amount, "ARS", effectiveFrom, "motivo", null));
    }

    private ResultActions postJson(String path, String token, String content, Object... variables) throws Exception {
        return mockMvc.perform(post(path, variables.length == 0 ? new Object[]{priceId} : variables)
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(content));
    }

    private long idOf(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private long amountOn(LocalDate date) {
        return priceReader.requirePriceVersionEffectiveOn(OfferingReference.ofProgram(programId), FeeType.MONTHLY_INSTALLMENT, date).finalAmount().value().amount().longValue();
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    @Test
    @Tag("D-223")
    @Tag("D-095")
    void directorPublishingWithStartTodayIsEffectiveWithoutAuthorizationRequestAndSupersedesTheOldVersion() throws Exception {
        long requestsBefore = count("select count(*) from authorization_request");

        publish(directorToken, 120000, TODAY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("EFFECTIVE"))
                .andExpect(jsonPath("$.directPublication").value(true))
                .andExpect(jsonPath("$.baseAmount.amount").value(120000.0))
                .andExpect(jsonPath("$.finalAmount.amount").value(120000.0));

        assertThat(count("select count(*) from authorization_request")).isEqualTo(requestsBefore);
        assertThat(count("select count(*) from price_version where price_id = ? and status = 'EFFECTIVE'", priceId)).isEqualTo(1);
        assertThat(count("select count(*) from price_version where price_id = ? and status = 'SUPERSEDED'", priceId)).isEqualTo(1);
        assertThat(amountOn(TODAY)).isEqualTo(120000);
    }

    @Test
    @Tag("D-111")
    void aStartDateInThePastIsRejectedAndTodayOrTomorrowAreAccepted() throws Exception {
        publish(directorToken, 120000, TODAY.minusDays(1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].code").value("RETROACTIVE_EFFECTIVE_DATE"));
        publish(directorToken, 120000, TODAY).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("EFFECTIVE"));
        publish(directorToken, 130000, TODAY.plusDays(1)).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("SCHEDULED"));
    }

    @Test
    @Tag("D-115")
    void aScheduledVersionCancelledWithAReasonLeavesThePreviousPriceInForceAndShowsTheReason() throws Exception {
        LocalDate decemberFirst = LocalDate.of(2027, 12, 1);
        long versionId = idOf(publish(directorToken, 120000, decemberFirst).andExpect(status().isCreated()));

        postJson("/price-versions/{id}/cancellation", directorToken, "{\"reason\":\"Se posterga el aumento\"}", versionId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("Se posterga el aumento"));

        assertThat(amountOn(decemberFirst)).isEqualTo(100000);
        assertThat(count("select count(*) from price_version_status_change where price_version_id = ? and to_status = 'CANCELLED'", versionId)).isEqualTo(1);
    }

    @Test
    @Tag("D-115")
    void cancellingWithoutReasonIs400AndCancellingOnTheStartDateIs409() throws Exception {
        LocalDate decemberFirst = LocalDate.of(2027, 12, 1);
        long versionId = idOf(publish(directorToken, 120000, decemberFirst));

        MutableClock.INSTANCE.setDate(decemberFirst.minusDays(1));
        postJson("/price-versions/{id}/cancellation", directorToken, "{}", versionId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].code").value("CANCELLATION_REASON_REQUIRED"));
        MutableClock.INSTANCE.setDate(decemberFirst);
        postJson("/price-versions/{id}/cancellation", directorToken, "{\"reason\":\"tarde\"}", versionId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.violations[0].code").value("VERSION_ALREADY_EFFECTIVE"));
    }

    @Test
    @Tag("D-277")
    void aSecondVersionWithTheSameStartDateReplacesTheFirstWhichIsCancelledWithAnAutomaticReason() throws Exception {
        LocalDate septemberFirst = LocalDate.of(2027, 9, 1);
        long first = idOf(publish(directorToken, 120000, septemberFirst));

        publish(directorToken, 125000, septemberFirst).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("SCHEDULED"));

        assertThat(jdbc.queryForObject("select status from price_version where id = ?", String.class, first)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("select cancellation_reason from price_version where id = ?", String.class, first))
                .isEqualTo("Reemplazada por otra versión con la misma fecha de inicio.");
        assertThat(amountOn(septemberFirst)).isEqualTo(125000);
    }

    @Test
    @Tag("D-277")
    void aSameDayCorrectionRulesFromThatMomentWhileThePreviousOneKeepsItsRowAsSuperseded() throws Exception {
        long morning = idOf(publish(directorToken, 110000, TODAY));

        publish(directorToken, 112000, TODAY).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("EFFECTIVE"));

        assertThat(jdbc.queryForObject("select status from price_version where id = ?", String.class, morning)).isEqualTo("SUPERSEDED");
        assertThat(count("select count(*) from price_version where price_id = ? and status = 'EFFECTIVE'", priceId)).isEqualTo(1);
        assertThat(amountOn(TODAY)).isEqualTo(112000);
    }

    @Test
    @Tag("D-280")
    @Tag("D-214")
    void anEarlierStartingVersionCoexistsWithALaterScheduledOneAndTheActivationJobWalksThemInOrder() throws Exception {
        LocalDate septemberFirst = LocalDate.of(2027, 9, 1);
        LocalDate octoberFirst = LocalDate.of(2027, 10, 1);
        long october = idOf(publish(directorToken, 120000, octoberFirst));
        long september = idOf(publish(directorToken, 110000, septemberFirst));

        assertThat(amountOn(LocalDate.of(2027, 8, 31))).isEqualTo(100000);
        assertThat(amountOn(LocalDate.of(2027, 9, 15))).isEqualTo(110000);
        assertThat(amountOn(octoberFirst)).isEqualTo(120000);

        MutableClock.INSTANCE.setDate(septemberFirst);
        assertThat(activator.activateDueVersions()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from price_version where id = ?", String.class, september)).isEqualTo("EFFECTIVE");
        assertThat(jdbc.queryForObject("select status from price_version where id = ?", String.class, october)).isEqualTo("SCHEDULED");
        assertThat(count("select count(*) from price_version where price_id = ? and status = 'SUPERSEDED'", priceId)).isEqualTo(1);

        MutableClock.INSTANCE.setDate(octoberFirst);
        assertThat(activator.activateDueVersions()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from price_version where id = ?", String.class, october)).isEqualTo("EFFECTIVE");
        assertThat(jdbc.queryForObject("select status from price_version where id = ?", String.class, september)).isEqualTo("SUPERSEDED");
        assertThat(count("select count(*) from price_version where price_id = ? and status = 'EFFECTIVE'", priceId)).isEqualTo(1);
        assertThat(activator.activateDueVersions()).isZero();
    }

    @Test
    @Tag("D-214")
    void theActivationJobRecordsTheProcessAsActorOfBothTransitions() throws Exception {
        LocalDate septemberFirst = LocalDate.of(2027, 9, 1);
        long scheduled = idOf(publish(directorToken, 120000, septemberFirst));

        MutableClock.INSTANCE.setDate(septemberFirst);
        activator.activateDueVersions();

        assertThat(count("select count(*) from price_version_status_change where price_version_id = ? and event = 'REACH_START' and actor_process = 'PriceVersionActivation'",
                scheduled)).isEqualTo(1);
        assertThat(count("select count(*) from price_version_status_change c join price_version v on v.id = c.price_version_id "
                + "where v.price_id = ? and c.event = 'SUPERSEDE' and c.actor_process = 'PriceVersionActivation'", priceId)).isEqualTo(1);
    }

    @Test
    @Tag("D-110")
    void everyRoleButTheDirectorIsForbiddenToPublishValidateOrCancel() throws Exception {
        long versionId = idOf(publish(directorToken, 120000, TODAY.plusDays(30)));
        for (String role : new String[]{"ADMINISTRATIVO", "PRECEPTOR", "DOCENTE", "ALUMNO"}) {
            String token = tokenWithRole(role);
            publish(token, 130000, TODAY.plusDays(40)).andExpect(status().isForbidden());
            postJson("/prices/{priceId}/versions/validation", token, body(130000, "ARS", TODAY.plusDays(40), "motivo", null)).andExpect(status().isForbidden());
            postJson("/price-versions/{id}/cancellation", token, "{\"reason\":\"motivo\"}", versionId).andExpect(status().isForbidden());
        }
        assertThat(count("select count(*) from price_version where id = ? and status = 'SCHEDULED'", versionId)).isEqualTo(1);
        assertThat(count("select count(*) from price_version where price_id = ?", priceId)).isEqualTo(2);
    }

    @Test
    @Tag("D-097")
    void aVersionInAnotherCurrencyIsRejectedAndValidationReportsItWithoutPersisting() throws Exception {
        long versionsBefore = count("select count(*) from price_version where price_id = ?", priceId);

        postJson("/prices/{priceId}/versions", directorToken, body(120000, "USD", TODAY.plusDays(5), "motivo", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].code").value("CURRENCY_MISMATCH"));
        postJson("/prices/{priceId}/versions/validation", directorToken, body(120000, "USD", TODAY.plusDays(5), "motivo", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.violations", hasSize(1)))
                .andExpect(jsonPath("$.violations[0].code").value("CURRENCY_MISMATCH"));
        postJson("/prices/{priceId}/versions/validation", directorToken, body(120000, "ARS", TODAY.plusDays(5), "motivo", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.violations", hasSize(0)));

        assertThat(count("select count(*) from price_version where price_id = ?", priceId)).isEqualTo(versionsBefore);
    }

    @Test
    @Tag("D-095")
    void theReasonAtTheColumnLimitIsAcceptedAndOneCharacterMoreIsA400NeverA500() throws Exception {
        String limit = "m".repeat(1000);

        publish(directorToken, 120000, TODAY.plusDays(2)).andExpect(status().isCreated());
        postJson("/prices/{priceId}/versions", directorToken, body(130000, "ARS", TODAY.plusDays(3), limit, null)).andExpect(status().isCreated());
        postJson("/prices/{priceId}/versions", directorToken, body(140000, "ARS", TODAY.plusDays(4), limit + "x", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].code").value("CHANGE_REASON_REQUIRED"));
        postJson("/prices/{priceId}/versions", directorToken, body(140000, "ARS", TODAY.plusDays(4), null, null))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Tag("D-240")
    void theMarketingDiscountIsStoredWithTheVersionAndTheFinalAmountIsBaseMinusDiscount() throws Exception {
        postJson("/prices/{priceId}/versions", directorToken, body(100000, "ARS", TODAY.plusDays(2), "campaña",
                "{\"type\":\"PERCENTAGE\",\"percent\":50}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.marketingDiscountPercent").value(50.0))
                .andExpect(jsonPath("$.marketingDiscountAmount.amount").value(50000.0))
                .andExpect(jsonPath("$.finalAmount.amount").value(50000.0));
        postJson("/prices/{priceId}/versions", directorToken, body(100000, "ARS", TODAY.plusDays(3), "campaña",
                "{\"type\":\"FIXED\",\"fixedAmount\":{\"amount\":30000,\"currency\":\"ARS\"}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.finalAmount.amount").value(70000.0));
        postJson("/prices/{priceId}/versions", directorToken, body(100000, "ARS", TODAY.plusDays(4), "campaña",
                "{\"type\":\"FIXED\",\"fixedAmount\":{\"amount\":150000,\"currency\":\"ARS\"}}"))
                .andExpect(status().isBadRequest());
        postJson("/prices/{priceId}/versions", directorToken, body(100000, "ARS", TODAY.plusDays(4), "campaña",
                "{\"type\":\"PERCENTAGE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void publishingOnAnUnknownPriceIs404() throws Exception {
        postJson("/prices/{priceId}/versions", directorToken, body(120000, "ARS", TODAY, "motivo", null), 999999999L).andExpect(status().isNotFound());
    }
}
