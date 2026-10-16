package com.red_escuela.backend.billing.pricing.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.billing.pricing.domain.Price;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.academicstructure.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.Address;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PriceReadControllerIT extends AbstractIntegrationTest {
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

    private String directorToken;
    private String administrativeToken;
    private long programId;
    private long priceId;

    @BeforeEach
    void setUp() throws Exception {
        MutableClock.INSTANCE.setDate(TODAY);
        directorToken = tokenWithRole("DIRECTOR");
        administrativeToken = tokenWithRole("ADMINISTRATIVO");
        String response = mockMvc.perform(post("/programs").header("Authorization", "Bearer " + administrativeToken).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Carrera de lectura " + System.nanoTime(), "totalYears", 3, "totalCreditsRequired", 100,
                                "price", Map.of("amount", 100000, "currency", "ARS")))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        programId = objectMapper.readTree(response).get("id").asLong();
        priceId = jdbc.queryForObject("select id from price where program_id = ? and fee_type = 'MONTHLY_INSTALLMENT'", Long.class, programId);
    }

    private Person persistPerson(String roleName) {
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
        return jwtService.generateAccessToken(persistPerson(roleName));
    }

    private void enrollStudentInTheProgram(ProgramEnrollmentStatus status) {
        Institution institution = new Institution();
        institution.setName("Instituto " + System.nanoTime());
        entityManager.persist(institution);
        Student student = new Student();
        student.setPerson(persistPerson("ALUMNO"));
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgram(entityManager.find(Program.class, programId));
        enrollment.setStatus(status);
        enrollment.setEnrollmentDate(LocalDateTime.of(2027, 3, 1, 10, 0));
        entityManager.persist(enrollment);
        entityManager.flush();
    }

    private static String body(long amount, LocalDate effectiveFrom, String discountJson) {
        return "{\"amount\":{\"amount\":" + amount + ",\"currency\":\"ARS\"},\"effectiveFrom\":\"" + effectiveFrom + "\",\"changeReason\":\"motivo\""
                + (discountJson == null ? "" : ",\"marketingDiscount\":" + discountJson) + "}";
    }

    private ResultActions publish(long amount, LocalDate effectiveFrom) throws Exception {
        return mockMvc.perform(post("/prices/{priceId}/versions", priceId).header("Authorization", "Bearer " + directorToken)
                .contentType(MediaType.APPLICATION_JSON).content(body(amount, effectiveFrom, null))).andExpect(status().isCreated());
    }

    private void activateDueVersionsOn(LocalDate day) {
        MutableClock.INSTANCE.setDate(day);
        entityManager.find(Price.class, priceId).activateDueVersions(day, MutableClock.INSTANCE.instant());
        entityManager.flush();
    }

    private JsonNode monthlyInstallmentSummary() throws Exception {
        String content = mockMvc.perform(get("/prices").param("programId", Long.toString(programId)).header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (JsonNode summary : objectMapper.readTree(content)) {
            if (summary.get("priceId").asLong() == priceId) {
                return summary;
            }
        }
        throw new AssertionError("The monthly installment price is not listed");
    }

    private static void assertAmount(JsonNode version, String expected) {
        assertThat(new BigDecimal(version.at("/finalAmount/amount").asText())).isEqualByComparingTo(expected);
    }

    private long idOf(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private ResultActions impact(String token, long amount, LocalDate effectiveFrom, String discountJson) throws Exception {
        return mockMvc.perform(post("/prices/{priceId}/versions/impact", priceId).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body(amount, effectiveFrom, discountJson)));
    }

    @Test
    @Tag("D-214")
    @Tag("D-110")
    void directorAndAdministrativeListThePriceWithItsCurrentVersionAndNothingElseWhenNothingIsPending() throws Exception {
        for (String token : new String[]{directorToken, administrativeToken}) {
            String content = mockMvc.perform(get("/prices").param("programId", Long.toString(programId)).header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            JsonNode prices = objectMapper.readTree(content);
            assertThat(prices).hasSize(2);
            JsonNode monthly = monthlyInstallmentSummary();
            assertAmount(monthly.get("currentVersion"), "100000");
            assertThat(monthly.get("nextScheduledVersion").isNull()).isTrue();
            assertThat(monthly.get("openProposal").isNull()).isTrue();
            assertThat(monthly.get("programId").asLong()).isEqualTo(programId);
            assertThat(monthly.get("courseId").isNull()).isTrue();
            assertThat(monthly.get("currency").asText()).isEqualTo("ARS");
        }
    }

    @Test
    @Tag("D-214")
    @Tag("D-280")
    void aListedPriceShowsTheCurrentVersionAndTheNextScheduledOneAndMovesOnWhenTheStartDayArrives() throws Exception {
        LocalDate septemberFirst = LocalDate.of(2027, 9, 1);
        LocalDate octoberFirst = LocalDate.of(2027, 10, 1);
        publish(120000, octoberFirst);
        publish(110000, septemberFirst);

        JsonNode before = monthlyInstallmentSummary();
        assertAmount(before.get("currentVersion"), "100000");
        assertAmount(before.get("nextScheduledVersion"), "110000");
        assertThat(before.at("/nextScheduledVersion/effectiveFrom").asText()).isEqualTo("2027-09-01");

        activateDueVersionsOn(septemberFirst);

        JsonNode after = monthlyInstallmentSummary();
        assertAmount(after.get("currentVersion"), "110000");
        assertAmount(after.get("nextScheduledVersion"), "120000");
    }

    @Test
    @Tag("D-214")
    void listingWithBothFiltersIs400AndFilteringByAnotherProgramReturnsOnlyItsOwnPrices() throws Exception {
        mockMvc.perform(get("/prices").param("programId", Long.toString(programId)).param("courseId", "1").header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/prices").param("programId", Long.toString(programId + 100000)).header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @Tag("D-110")
    void everyRoleButDirectorAndAdministrativeIsForbiddenToReadPricesHistoryAndImpact() throws Exception {
        for (String role : new String[]{"PRECEPTOR", "DOCENTE", "ALUMNO"}) {
            String token = tokenWithRole(role);
            mockMvc.perform(get("/prices").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
            mockMvc.perform(get("/prices/{priceId}/versions", priceId).header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
            impact(token, 120000, TODAY, null).andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/prices")).andExpect(status().isForbidden());
    }

    @Test
    @Tag("D-214")
    @Tag("D-115")
    void theHistoryListsEveryVersionNewestFirstWithItsStatusReasonsAndActors() throws Exception {
        long scheduled = idOf(publish(120000, LocalDate.of(2027, 12, 1)));
        mockMvc.perform(post("/price-versions/{id}/cancellation", scheduled).header("Authorization", "Bearer " + directorToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Se posterga el aumento\"}")).andExpect(status().isOk());
        publish(130000, TODAY);

        mockMvc.perform(get("/prices/{priceId}/versions", priceId).header("Authorization", "Bearer " + administrativeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$[0].effectiveFrom").value("2027-12-01"))
                .andExpect(jsonPath("$[0].cancellationReason").value("Se posterga el aumento"))
                .andExpect(jsonPath("$[0].cancelledById").isNumber())
                .andExpect(jsonPath("$[0].directPublication").value(true))
                .andExpect(jsonPath("$[0].statusChanges", hasSize(2)))
                .andExpect(jsonPath("$[0].statusChanges[1].toStatus").value("CANCELLED"))
                .andExpect(jsonPath("$[0].statusChanges[1].reason").value("Se posterga el aumento"))
                .andExpect(jsonPath("$[1].status").value("EFFECTIVE"))
                .andExpect(jsonPath("$[1].finalAmount.amount").value(130000.0))
                .andExpect(jsonPath("$[1].proposedById").isNumber())
                .andExpect(jsonPath("$[2].status").value("SUPERSEDED"))
                .andExpect(jsonPath("$[2].initialVersion").value(true))
                .andExpect(jsonPath("$[2].statusChanges[1].toStatus").value("SUPERSEDED"))
                .andExpect(jsonPath("$[2].statusChanges[1].actorPersonId").isNumber());
    }

    @Test
    @Tag("D-214")
    void aVersionActivatedByTheJobShowsTheProcessAsItsActorInTheHistory() throws Exception {
        LocalDate septemberFirst = LocalDate.of(2027, 9, 1);
        publish(120000, septemberFirst);
        activateDueVersionsOn(septemberFirst);

        mockMvc.perform(get("/prices/{priceId}/versions", priceId).header("Authorization", "Bearer " + directorToken))
                .andExpect(jsonPath("$[0].status").value("EFFECTIVE"))
                .andExpect(jsonPath("$[0].statusChanges[1].toStatus").value("EFFECTIVE"))
                .andExpect(jsonPath("$[0].statusChanges[1].actorProcess").value("PriceVersionActivation"))
                .andExpect(jsonPath("$[0].statusChanges[1].actorPersonId").value(nullValue()))
                .andExpect(jsonPath("$[1].status").value("SUPERSEDED"));
    }

    @Test
    @Tag("D-214")
    void theHistoryOfAPriceThatDoesNotExistIs404() throws Exception {
        mockMvc.perform(get("/prices/{priceId}/versions", priceId + 100000).header("Authorization", "Bearer " + directorToken)).andExpect(status().isNotFound());
    }

    @Test
    @Tag("D-274")
    @Tag("D-240")
    void theImpactCountsOnlyTheActiveEnrollmentsOfTheOfferingAndReportsTheMonthlyDifference() throws Exception {
        enrollStudentInTheProgram(ProgramEnrollmentStatus.ENROLLED);
        enrollStudentInTheProgram(ProgramEnrollmentStatus.ENROLLED);
        enrollStudentInTheProgram(ProgramEnrollmentStatus.PAUSED);
        enrollStudentInTheProgram(ProgramEnrollmentStatus.WITHDRAWN);
        enrollStudentInTheProgram(ProgramEnrollmentStatus.COMPLETED);

        for (String token : new String[]{directorToken, administrativeToken}) {
            impact(token, 120000, LocalDate.of(2027, 9, 1), null)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reachesActiveEnrollments").value(true))
                    .andExpect(jsonPath("$.affectedStudents").value(3))
                    .andExpect(jsonPath("$.currentFinalAmount.amount").value(100000.0))
                    .andExpect(jsonPath("$.proposedFinalAmount.amount").value(120000.0))
                    .andExpect(jsonPath("$.currentMonthlyIncome.amount").value(300000.0))
                    .andExpect(jsonPath("$.proposedMonthlyIncome.amount").value(360000.0))
                    .andExpect(jsonPath("$.monthlyIncomeDifference.amount").value(60000.0))
                    .andExpect(jsonPath("$.monthlyMarketingDiscountCost.amount").value(0.0));
        }
    }

    @Test
    @Tag("D-274")
    @Tag("D-240")
    void theImpactOfALowerFinalPriceIsANegativeDifferenceAndShowsWhatTheMarketingDiscountGivesUp() throws Exception {
        enrollStudentInTheProgram(ProgramEnrollmentStatus.ENROLLED);
        enrollStudentInTheProgram(ProgramEnrollmentStatus.ENROLLED);

        impact(directorToken, 120000, TODAY, "{\"type\":\"PERCENTAGE\",\"percent\":50}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proposedFinalAmount.amount").value(60000.0))
                .andExpect(jsonPath("$.monthlyIncomeDifference.amount").value(-80000.0))
                .andExpect(jsonPath("$.monthlyMarketingDiscountCost.amount").value(120000.0));
    }

    @Test
    @Tag("D-274")
    @Tag("D-280")
    void theImpactOfAVersionStartingBeforeAnAlreadyScheduledIncreaseIsComparedWithThePriceThatRulesJustBeforeIt() throws Exception {
        enrollStudentInTheProgram(ProgramEnrollmentStatus.ENROLLED);
        publish(120000, LocalDate.of(2027, 10, 1));

        impact(directorToken, 110000, LocalDate.of(2027, 9, 1), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentFinalAmount.amount").value(100000.0))
                .andExpect(jsonPath("$.monthlyIncomeDifference.amount").value(10000.0));
        impact(directorToken, 130000, LocalDate.of(2027, 11, 1), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentFinalAmount.amount").value(120000.0))
                .andExpect(jsonPath("$.monthlyIncomeDifference.amount").value(10000.0));
    }

    @Test
    @Tag("D-274")
    void theImpactOfAnEnrollmentFeeReachesNoActiveEnrollmentBecauseItAppliesOnlyToNewOnes() throws Exception {
        enrollStudentInTheProgram(ProgramEnrollmentStatus.ENROLLED);
        long enrollmentFeePriceId = jdbc.queryForObject("select id from price where program_id = ? and fee_type = 'ENROLLMENT_FEE'", Long.class, programId);

        mockMvc.perform(post("/prices/{priceId}/versions/impact", enrollmentFeePriceId).header("Authorization", "Bearer " + directorToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body(50000, TODAY, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reachesActiveEnrollments").value(false))
                .andExpect(jsonPath("$.affectedStudents").value(0))
                .andExpect(jsonPath("$.monthlyIncomeDifference.amount").value(0.0));
    }

    @Test
    @Tag("D-274")
    @Tag("D-097")
    void theImpactOfAnInvalidCandidateIs400AndOfAMissingPriceIs404WithoutChangingAnything() throws Exception {
        long versionsBefore = jdbc.queryForObject("select count(*) from price_version where price_id = ?", Long.class, priceId);

        mockMvc.perform(post("/prices/{priceId}/versions/impact", priceId).header("Authorization", "Bearer " + directorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":{\"amount\":120000,\"currency\":\"USD\"},\"effectiveFrom\":\"" + TODAY + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].code").value("CURRENCY_MISMATCH"));
        mockMvc.perform(post("/prices/{priceId}/versions/impact", priceId + 100000).header("Authorization", "Bearer " + directorToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body(120000, TODAY, null)))
                .andExpect(status().isNotFound());

        assertThat(jdbc.queryForObject("select count(*) from price_version where price_id = ?", Long.class, priceId)).isEqualTo(versionsBefore);
    }
}
