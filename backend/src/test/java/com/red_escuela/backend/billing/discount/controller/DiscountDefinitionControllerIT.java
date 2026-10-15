package com.red_escuela.backend.billing.discount.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Tag("D-217")
class DiscountDefinitionControllerIT extends AbstractIntegrationTest {
    private static final LocalDate TODAY = LocalDate.of(2031, 3, 10);
    private static final String BASE = "/discount-definitions";

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

    private Long lastPersonId;
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
        lastPersonId = person.getId();
        return jwtService.generateAccessToken(person);
    }

    private static Map<String, Object> percent(String points) {
        Map<String, Object> calculation = new LinkedHashMap<>();
        calculation.put("type", "PROPORTIONAL");
        calculation.put("ratePercent", new java.math.BigDecimal(points));
        return calculation;
    }

    private static Map<String, Object> fixedAmount(String amount, String currency) {
        Map<String, Object> calculation = new LinkedHashMap<>();
        calculation.put("type", "NOMINAL");
        calculation.put("amount", money(amount, currency));
        return calculation;
    }

    private static Map<String, Object> money(String amount, String currency) {
        Map<String, Object> money = new LinkedHashMap<>();
        money.put("amount", amount);
        money.put("currency", currency);
        return money;
    }

    private static Map<String, Object> cohortPromotion(String name, Long programId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("discountType", "COHORT_PROMOTION");
        body.put("applicableInvoiceType", "MONTHLY_INSTALLMENT");
        body.put("calculation", percent("10"));
        body.put("installmentsTotal", 3);
        body.put("availableFrom", TODAY.minusDays(30).toString());
        body.put("priority", 1);
        body.put("programId", programId);
        return body;
    }

    private static Map<String, Object> annualUpfront(String name) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("discountType", "ANNUAL_UPFRONT");
        body.put("applicableInvoiceType", "ANNUAL_UPFRONT");
        body.put("calculation", percent("15"));
        body.put("availableFrom", TODAY.toString());
        body.put("priority", 1);
        return body;
    }

    private ResultActions send(String method, String path, String token, Object body) throws Exception {
        var request = "GET".equals(method) ? get(BASE + path) : post(BASE + path).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
        return mockMvc.perform(token == null ? request : request.header("Authorization", "Bearer " + token));
    }

    private ResultActions postAs(String token, Object body) throws Exception {
        return send("POST", "", token, body);
    }

    private JsonNode createdAsDirector(Map<String, Object> body) throws Exception {
        return readBody(postAs(directorToken, body).andExpect(status().isCreated()));
    }

    private JsonNode readBody(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private JsonNode catalog(String query) throws Exception {
        return readBody(mockMvc.perform(get(BASE + query).header("Authorization", "Bearer " + directorToken)).andExpect(status().isOk()));
    }

    private JsonNode entryNamed(JsonNode catalog, String name) {
        return StreamSupport.stream(catalog.spliterator(), false).filter(entry -> name.equals(entry.get("name").asText())).findFirst()
                .orElseThrow(() -> new AssertionError("The catalog has no definition named " + name));
    }

    private boolean catalogHas(String name) throws Exception {
        return StreamSupport.stream(catalog("").spliterator(), false).anyMatch(entry -> name.equals(entry.get("name").asText()));
    }

    private long countDefinitions() {
        return jdbc.queryForObject("select count(*) from discount_definition", Long.class);
    }

    @Test
    @Tag("D-176")
    void a09AdministrativeCreationAnswers202AndLeavesAPendingRequestWithoutCreatingTheDefinition() throws Exception {
        long before = countDefinitions();

        JsonNode pending = readBody(postAs(administrativeToken, annualUpfront("a09 propuesta")).andExpect(status().isAccepted()).andExpect(jsonPath("$.authorizationRequestId").isNumber()));

        assertThat(countDefinitions()).isEqualTo(before);
        assertThat(catalogHas("a09 propuesta")).isFalse();
        assertThat(jdbc.queryForObject("select status from authorization_request where id = ?", String.class, pending.get("authorizationRequestId").asLong())).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("select operation_type from authorization_request where id = ?", String.class, pending.get("authorizationRequestId").asLong()))
                .isEqualTo("CREATE_DISCOUNT_DEFINITION");
    }

    @Test
    @Tag("D-223")
    void a10DirectorCreationAnswers201ActiveWithoutAnyAuthorizationRequest() throws Exception {
        long requestsBefore = jdbc.queryForObject("select count(*) from authorization_request", Long.class);

        JsonNode created = createdAsDirector(annualUpfront("a10 directa"));

        assertThat(created.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(created.get("directPublication").asBoolean()).isTrue();
        assertThat(created.get("authorizationRequestId").isNull()).isTrue();
        assertThat(created.get("calculation").get("type").asText()).isEqualTo("PROPORTIONAL");
        assertThat(created.get("calculation").get("ratePercent").decimalValue()).isEqualByComparingTo("15");
        assertThat(jdbc.queryForObject("select count(*) from authorization_request", Long.class)).isEqualTo(requestsBefore);
        assertThat(entryNamed(catalog("?status=ACTIVE"), "a10 directa").get("id").asLong()).isEqualTo(created.get("id").asLong());
    }

    @Test
    @Tag("D-223")
    void theRoleOfTheCallerAndNeverTheBodyDecidesWhoPublishesDirectly() throws Exception {
        Map<String, Object> body = annualUpfront("cuerpo con rol falso");
        body.put("requestedByDirector", true);
        body.put("directPublication", true);

        postAs(administrativeToken, body).andExpect(status().isAccepted());

        assertThat(catalogHas("cuerpo con rol falso")).isFalse();
    }

    @Test
    @Tag("D-240")
    @Tag("D-326")
    void a08EveryDefinitionCostsItsOwnQuotaOnTheFinalPriceInForceTodayOfItsOwnFee() throws Exception {
        var offering = factory.newProgram(Currency.ARS, 100000, 50000);
        Map<String, Object> withQuota = cohortPromotion("a08 con cupo", offering.programId());
        withQuota.put("quota", 2);
        Map<String, Object> withoutQuota = cohortPromotion("a08 sin cupo", offering.programId());
        Map<String, Object> withoutInstallments = cohortPromotion("a08 sin cuotas", offering.programId());
        withoutInstallments.put("installmentsTotal", null);
        withoutInstallments.put("quota", 2);
        Map<String, Object> enrollment = cohortPromotion("a08 matricula", offering.programId());
        enrollment.put("applicableInvoiceType", "ENROLLMENT_FEE");
        enrollment.put("installmentsTotal", null);
        enrollment.put("calculation", percent("20"));
        enrollment.put("quota", 50);
        Map<String, Object> cappedFixed = cohortPromotion("a08 fijo con tope", offering.programId());
        cappedFixed.put("calculation", fixedAmount("5000.00", "ARS"));
        cappedFixed.put("capPerInvoice", money("4000.00", "ARS"));
        cappedFixed.put("quota", 2);
        List.of(withQuota, withoutQuota, withoutInstallments, enrollment, cappedFixed).forEach(body -> {
            try {
                createdAsDirector(body);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        factory.newPrice(offering, FeeType.MONTHLY_INSTALLMENT, 120000, TODAY.plusMonths(1));

        JsonNode today = catalog("?type=COHORT_PROMOTION");

        assertThat(entryNamed(today, "a08 con cupo").get("estimatedQuotaCost").get("amount").asText()).isEqualTo("60000.00");
        assertThat(entryNamed(today, "a08 con cupo").get("estimatedQuotaCost").get("currency").asText()).isEqualTo("ARS");
        assertThat(entryNamed(today, "a08 con cupo").get("estimatedQuotaCostStatus").asText()).isEqualTo("ESTIMATED");
        assertThat(entryNamed(today, "a08 sin cupo").get("estimatedQuotaCost").isNull()).isTrue();
        assertThat(entryNamed(today, "a08 sin cupo").get("estimatedQuotaCostStatus").asText()).isEqualTo("NO_QUOTA");
        assertThat(entryNamed(today, "a08 sin cuotas").get("estimatedQuotaCost").isNull()).isTrue();
        assertThat(entryNamed(today, "a08 sin cuotas").get("estimatedQuotaCostStatus").asText()).isEqualTo("NOT_APPLICABLE");
        assertThat(entryNamed(today, "a08 matricula").get("estimatedQuotaCost").get("amount").asText()).isEqualTo("500000.00");
        assertThat(entryNamed(today, "a08 fijo con tope").get("estimatedQuotaCost").get("amount").asText()).isEqualTo("24000.00");

        MutableClock.INSTANCE.setDate(TODAY.plusMonths(1));
        JsonNode afterTheIncrease = catalog("?type=COHORT_PROMOTION");

        assertThat(entryNamed(afterTheIncrease, "a08 con cupo").get("estimatedQuotaCost").get("amount").asText()).isEqualTo("72000.00");
        assertThat(entryNamed(afterTheIncrease, "a08 matricula").get("estimatedQuotaCost").get("amount").asText()).isEqualTo("500000.00");
        assertThat(entryNamed(afterTheIncrease, "a08 fijo con tope").get("estimatedQuotaCost").get("amount").asText()).isEqualTo("24000.00");
    }

    @Test
    @Tag("D-240")
    void aProgramPromotionWithQuotaWithoutAPriceInForceTodayReportsNoEffectivePriceUntilThePriceStarts() throws Exception {
        var offering = factory.newProgram(Currency.ARS, 100000, 50000);
        Map<String, Object> body = cohortPromotion("a08 sin precio vigente", offering.programId());
        body.put("quota", 2);
        createdAsDirector(body);

        MutableClock.INSTANCE.setDate(LocalDate.of(2019, 12, 31));
        JsonNode beforeThePrice = entryNamed(catalog("?type=COHORT_PROMOTION"), "a08 sin precio vigente");
        MutableClock.INSTANCE.setDate(LocalDate.of(2020, 1, 1));
        JsonNode onTheFirstDay = entryNamed(catalog("?type=COHORT_PROMOTION"), "a08 sin precio vigente");

        assertThat(beforeThePrice.get("estimatedQuotaCostStatus").asText()).isEqualTo("NO_EFFECTIVE_PRICE");
        assertThat(beforeThePrice.get("estimatedQuotaCost").isNull()).isTrue();
        assertThat(onTheFirstDay.get("estimatedQuotaCostStatus").asText()).isEqualTo("ESTIMATED");
        assertThat(onTheFirstDay.get("estimatedQuotaCost").get("amount").asText()).isEqualTo("60000.00");
    }

    @Test
    @Tag("D-240")
    void aCoursePromotionIsPricedOnTheCoursePriceAndWithoutOneReportsNoEffectivePrice() throws Exception {
        Long pricedCourseId = persistCourse(true);
        Long unpricedCourseId = persistCourse(false);
        Map<String, Object> priced = cohortPromotion("a08 curso con precio", null);
        priced.put("courseId", pricedCourseId);
        priced.put("quota", 2);
        Map<String, Object> unpriced = cohortPromotion("a08 curso sin precio", null);
        unpriced.put("courseId", unpricedCourseId);
        unpriced.put("quota", 2);
        createdAsDirector(priced);
        createdAsDirector(unpriced);

        JsonNode catalog = catalog("?type=COHORT_PROMOTION");

        assertThat(entryNamed(catalog, "a08 curso con precio").get("estimatedQuotaCostStatus").asText()).isEqualTo("ESTIMATED");
        assertThat(entryNamed(catalog, "a08 curso con precio").get("estimatedQuotaCost").get("amount").asText()).isEqualTo("48000.00");
        assertThat(entryNamed(catalog, "a08 curso sin precio").get("estimatedQuotaCostStatus").asText()).isEqualTo("NO_EFFECTIVE_PRICE");
    }

    @Test
    @Tag("D-240")
    void quotaUsedIsADocumentedStubOfZeroUntilStudentDiscountsExist() throws Exception {
        var offering = factory.newProgram(Currency.ARS, 100000, 50000);
        Map<String, Object> body = cohortPromotion("cupo sin usar", offering.programId());
        body.put("quota", 5);
        createdAsDirector(body);

        assertThat(entryNamed(catalog(""), "cupo sin usar").get("quotaUsed").asLong()).isZero();
    }

    @Test
    @Tag("D-240")
    void aDefinitionWithoutAnOfferingScopeReportsNotApplicableOrNoQuota() throws Exception {
        createdAsDirector(annualUpfront("pago anual sin alcance"));

        assertThat(entryNamed(catalog(""), "pago anual sin alcance").get("estimatedQuotaCostStatus").asText()).isEqualTo("NO_QUOTA");
    }

    private Long persistCourse(boolean withPrice) {
        com.red_escuela.backend.academicstructure.entity.Term term = new com.red_escuela.backend.academicstructure.entity.Term();
        term.setYear(2031);
        term.setStatus(StatusType.ACTIVE);
        entityManager.persist(term);
        com.red_escuela.backend.academicstructure.entity.Course course = new com.red_escuela.backend.academicstructure.entity.Course();
        course.setName("Curso " + System.nanoTime());
        course.setTerm(term);
        course.setCapacity(20);
        course.setIsActive(StatusType.ACTIVE);
        entityManager.persist(course);
        if (withPrice) {
            com.red_escuela.backend.billing.support.PriceFixtures.persistPriceVersion(null, course.getId(), FeeType.MONTHLY_INSTALLMENT, "Cuota del curso",
                    com.red_escuela.backend.shared.domain.Money.of(new java.math.BigDecimal("80000"), Currency.ARS), TODAY.minusDays(1), lastPersonId);
        }
        return course.getId();
    }

    @Test
    @Tag("D-283")
    void theCatalogReportsEachDefinitionAsUpcomingAvailableOrExpiredOnItsOwnDates() throws Exception {
        Map<String, Object> upcoming = annualUpfront("disponibilidad futura");
        upcoming.put("availableFrom", TODAY.plusDays(1).toString());
        Map<String, Object> lastDay = annualUpfront("disponibilidad hasta hoy");
        lastDay.put("availableUntil", TODAY.toString());
        Map<String, Object> expired = annualUpfront("disponibilidad vencida");
        expired.put("availableFrom", TODAY.minusDays(10).toString());
        expired.put("availableUntil", TODAY.minusDays(1).toString());
        for (Map<String, Object> body : List.of(upcoming, lastDay, expired)) {
            createdAsDirector(body);
        }

        JsonNode catalog = catalog("?type=ANNUAL_UPFRONT");

        assertThat(entryNamed(catalog, "disponibilidad futura").get("availability").asText()).isEqualTo("UPCOMING");
        assertThat(entryNamed(catalog, "disponibilidad hasta hoy").get("availability").asText()).isEqualTo("AVAILABLE");
        assertThat(entryNamed(catalog, "disponibilidad vencida").get("availability").asText()).isEqualTo("EXPIRED");
    }

    @Test
    void theCatalogFiltersByTypeAndStatus() throws Exception {
        Long closedId = createdAsDirector(annualUpfront("filtro cerrada")).get("id").asLong();
        createdAsDirector(annualUpfront("filtro activa"));
        send("POST", "/" + closedId + "/closure", directorToken, Map.of("reason", "ya no se usa")).andExpect(status().isCreated());

        JsonNode closed = catalog("?type=ANNUAL_UPFRONT&status=CLOSED");
        JsonNode active = catalog("?type=ANNUAL_UPFRONT&status=ACTIVE");
        JsonNode otherType = catalog("?type=SCHOLARSHIP");

        assertThat(StreamSupport.stream(closed.spliterator(), false).map(entry -> entry.get("name").asText())).contains("filtro cerrada").doesNotContain("filtro activa");
        assertThat(StreamSupport.stream(active.spliterator(), false).map(entry -> entry.get("name").asText())).contains("filtro activa").doesNotContain("filtro cerrada");
        assertThat(StreamSupport.stream(otherType.spliterator(), false).map(entry -> entry.get("discountType").asText())).containsOnly("SCHOLARSHIP");
        assertThat(otherType.size()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @Tag("D-157")
    void validationReportsEveryViolationAtOnceAndPersistsNothing() throws Exception {
        long before = countDefinitions();
        Map<String, Object> body = annualUpfront("   ");
        body.put("discountType", "SCHOLARSHIP");
        body.put("applicableInvoiceType", "ENROLLMENT_FEE");
        body.put("priority", 0);

        send("POST", "/validation", administrativeToken, body).andExpect(status().isOk())
                .andExpect(jsonPath("$.violations[*].code").value(hasItems("INVALID_DISCOUNT_DEFINITION", "SCHOLARSHIP_ONLY_FOR_MONTHLY_INSTALLMENTS")))
                .andExpect(jsonPath("$.violations", hasSize(org.hamcrest.Matchers.greaterThanOrEqualTo(2))));

        assertThat(countDefinitions()).isEqualTo(before);
    }

    @Test
    @Tag("D-075")
    void validationWithAPercentBeyondTheLimitsOrANegativeAmountAnswers400WithTheValueCodeAndNoViolations() throws Exception {
        Map<String, Object> overOneHundred = annualUpfront("más de cien");
        overOneHundred.put("calculation", percent("101"));
        Map<String, Object> negative = annualUpfront("negativo");
        negative.put("calculation", fixedAmount("-5.00", "ARS"));

        send("POST", "/validation", administrativeToken, overOneHundred).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PERCENTAGE"))
                .andExpect(jsonPath("$.violations").doesNotExist());
        send("POST", "/validation", administrativeToken, negative).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("NEGATIVE_ADJUSTMENT_AMOUNT"));
    }

    @Test
    @Tag("D-240")
    void aScopeOfANonexistentProgramOrCohortAnswers404OnValidationAndOnCreation() throws Exception {
        Map<String, Object> missingProgram = cohortPromotion("programa inexistente", Long.MAX_VALUE);
        var offering = factory.newProgram(Currency.ARS, 100000, 50000);
        Map<String, Object> missingCohort = cohortPromotion("cohorte inexistente", offering.programId());
        missingCohort.put("programCohortId", Long.MAX_VALUE);

        send("POST", "/validation", administrativeToken, missingProgram).andExpect(status().isNotFound());
        postAs(directorToken, missingProgram).andExpect(status().isNotFound());
        send("POST", "/validation", administrativeToken, missingCohort).andExpect(status().isNotFound());
        postAs(directorToken, missingCohort).andExpect(status().isNotFound());
    }

    @Test
    void validationOfACoherentDraftReportsNoViolations() throws Exception {
        send("POST", "/validation", directorToken, annualUpfront("validación sana")).andExpect(status().isOk()).andExpect(jsonPath("$.violations", hasSize(0)));
    }

    @Test
    @Tag("D-287")
    void creatingACohortEntryDiscountAnswers400WithTheCatalogCode() throws Exception {
        Map<String, Object> body = annualUpfront("ingreso");
        body.put("discountType", "COHORT_ENTRY");

        postAs(directorToken, body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DISCOUNT_TYPE_NOT_IN_CATALOG"));
    }

    @Test
    @Tag("D-097")
    void a12AFixedAmountInPesosForAProgramInDollarsAnswers400CurrencyMismatch() throws Exception {
        var inDollars = factory.newProgram(Currency.USD, 300, 100);
        Map<String, Object> body = cohortPromotion("monto en pesos", inDollars.programId());
        body.put("calculation", fixedAmount("5000.00", "ARS"));

        postAs(directorToken, body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CURRENCY_MISMATCH"))
                .andExpect(jsonPath("$.violations[0].code").value("CURRENCY_MISMATCH"));
    }

    @Test
    @Tag("D-075")
    void aZeroPercentOrMoreThanOneHundredPercentAnswers400() throws Exception {
        Map<String, Object> zero = annualUpfront("cero");
        zero.put("calculation", percent("0"));
        Map<String, Object> zeroFixed = annualUpfront("cero fijo");
        zeroFixed.put("calculation", fixedAmount("0.00", "ARS"));
        Map<String, Object> overOneHundred = annualUpfront("más de cien");
        overOneHundred.put("calculation", percent("100.01"));

        postAs(directorToken, zero).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_DISCOUNT_DEFINITION"));
        postAs(directorToken, zeroFixed).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_DISCOUNT_DEFINITION"));
        postAs(directorToken, overOneHundred).andExpect(status().isBadRequest());
    }

    @Test
    void aMissingPriorityIsRejectedByThePolicyAndNeverByAnException() throws Exception {
        Map<String, Object> body = annualUpfront("sin prioridad");
        body.remove("priority");

        postAs(directorToken, body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_DISCOUNT_DEFINITION"));
    }

    @Test
    @Tag("D-075")
    void aCapBeyondThePrecisionOfItsColumnAnswers400AndTheLargestCapIsStored() throws Exception {
        Map<String, Object> tooLarge = annualUpfront("tope enorme");
        tooLarge.put("capPerInvoice", money("10000000000.00", "ARS"));
        Map<String, Object> largest = annualUpfront("tope máximo");
        largest.put("capPerInvoice", money("9999999999.99", "ARS"));

        postAs(directorToken, tooLarge).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_DISCOUNT_DEFINITION"));
        postAs(directorToken, largest).andExpect(status().isCreated()).andExpect(jsonPath("$.capPerInvoice.amount").value("9999999999.99"));
    }

    @Test
    void aNameOfTwoHundredCharactersPaddedWithBlanksFitsTheAuthorizationRequestColumn() throws Exception {
        String name = "n".repeat(200);
        Map<String, Object> proposal = annualUpfront(" ".repeat(400) + name + " ".repeat(400));

        postAs(administrativeToken, proposal).andExpect(status().isAccepted());

        assertThat(jdbc.queryForObject("select reason from authorization_request order by id desc limit 1", String.class)).isEqualTo("Alta de la promoción " + name);
    }

    @Test
    @Tag("D-036")
    void closingAsDirectorAnswers201ClosedWithTheTrimmedReasonAndAdministrativeClosingWaitsForApproval() throws Exception {
        Long directId = createdAsDirector(annualUpfront("cierre directo")).get("id").asLong();
        Long proposedId = createdAsDirector(annualUpfront("cierre propuesto")).get("id").asLong();

        send("POST", "/" + directId + "/closure", directorToken, Map.of("reason", "  Ya no se ofrece  ")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CLOSED")).andExpect(jsonPath("$.closureReason").value("Ya no se ofrece"));
        send("POST", "/" + proposedId + "/closure", administrativeToken, Map.of("reason", "Cambio de política")).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.authorizationRequestId").isNumber());

        assertThat(entryNamed(catalog(""), "cierre propuesto").get("status").asText()).isEqualTo("ACTIVE");
        assertThat(entryNamed(catalog(""), "cierre directo").get("status").asText()).isEqualTo("CLOSED");
    }

    @Test
    @Tag("D-217")
    void closingNeedsAReasonOfUpToFourHundredAndFiftyCharacters() throws Exception {
        Long id = createdAsDirector(annualUpfront("motivo de cierre")).get("id").asLong();
        Map<String, Object> missing = new LinkedHashMap<>();
        missing.put("reason", null);

        send("POST", "/" + id + "/closure", directorToken, missing).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DISCOUNT_CLOSURE_REASON_REQUIRED"));
        send("POST", "/" + id + "/closure", directorToken, Map.of("reason", "   ")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DISCOUNT_CLOSURE_REASON_REQUIRED"));
        send("POST", "/" + id + "/closure", directorToken, Map.of("reason", "r".repeat(451))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DISCOUNT_CLOSURE_REASON_REQUIRED"));
        send("POST", "/" + id + "/closure", administrativeToken, Map.of("reason", " ".repeat(300) + "r".repeat(450) + " ".repeat(300))).andExpect(status().isAccepted());

        assertThat(jdbc.queryForObject("select length(reason) from authorization_request order by id desc limit 1", Integer.class)).isLessThanOrEqualTo(500);
    }

    @Test
    @Tag("D-217")
    void closingAClosedOrUnknownDefinitionOpensNoRequest() throws Exception {
        Long id = createdAsDirector(annualUpfront("ya cerrada")).get("id").asLong();
        send("POST", "/" + id + "/closure", directorToken, Map.of("reason", "primera")).andExpect(status().isCreated());
        long requestsBefore = jdbc.queryForObject("select count(*) from authorization_request", Long.class);

        send("POST", "/" + id + "/closure", administrativeToken, Map.of("reason", "segunda")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ILLEGAL_DISCOUNT_DEFINITION_TRANSITION"));
        send("POST", "/" + Long.MAX_VALUE + "/closure", administrativeToken, Map.of("reason", "no existe")).andExpect(status().isNotFound());

        assertThat(jdbc.queryForObject("select count(*) from authorization_request", Long.class)).isEqualTo(requestsBefore);
    }

    @ParameterizedTest
    @Tag("D-110")
    @CsvSource({"PRECEPTOR", "DOCENTE", "ALUMNO"})
    void a13NoOtherRoleCanTouchAnyCatalogEndpoint(String roleName) throws Exception {
        String token = tokenWithRole(roleName);

        send("GET", "", token, null).andExpect(status().isForbidden());
        send("POST", "", token, annualUpfront("no autorizado")).andExpect(status().isForbidden());
        send("POST", "/validation", token, annualUpfront("no autorizado")).andExpect(status().isForbidden());
        send("POST", "/1/closure", token, Map.of("reason", "no autorizado")).andExpect(status().isForbidden());
    }

    @Test
    @Tag("D-110")
    void anUnauthenticatedCallerCannotTouchTheCatalog() throws Exception {
        send("GET", "", null, null).andExpect(status().isForbidden());
        send("POST", "", null, annualUpfront("anónimo")).andExpect(status().isForbidden());
    }

    @Test
    @Tag("D-110")
    void bothAdministrativeAndDirectorReadTheCatalog() throws Exception {
        send("GET", "", administrativeToken, null).andExpect(status().isOk());
        send("GET", "", directorToken, null).andExpect(status().isOk());
    }
}
