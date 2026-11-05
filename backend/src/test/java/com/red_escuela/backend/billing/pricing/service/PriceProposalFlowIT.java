package com.red_escuela.backend.billing.pricing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.billing.authorization.domain.AuthorizationResumeOutcome;
import com.red_escuela.backend.billing.authorization.repository.AuthorizationRequestRepository;
import com.red_escuela.backend.billing.authorization.service.AuthorizationRequestExpirer;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.AddressRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.pricing.OfferingReference;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class PriceProposalFlowIT extends AbstractIntegrationTest {
    private static final LocalDate TODAY = LocalDate.of(2027, 8, 20);
    private static final LocalDate SEPTEMBER_FIRST = LocalDate.of(2027, 9, 1);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private AddressRepository addressRepository;
    @Autowired
    private PersonRepository personRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PriceReader priceReader;
    @Autowired
    private AuthorizationRequestExpirer expirer;
    @Autowired
    private AuthorizationRequestRepository authorizationRequests;

    private Person director;
    private Person administrative;
    private String directorToken;
    private String administrativeToken;
    private long programId;
    private long priceId;
    private long enrollmentFeePriceId;

    @BeforeEach
    void setUp() throws Exception {
        MutableClock.INSTANCE.setDate(TODAY);
        director = person("director", "DIRECTOR");
        administrative = person("administrative", "ADMINISTRATIVO");
        directorToken = jwtService.generateAccessToken(director);
        administrativeToken = jwtService.generateAccessToken(administrative);
        String response = mockMvc.perform(post("/programs").header("Authorization", "Bearer " + administrativeToken).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Carrera de propuestas " + System.nanoTime(), "totalYears", 3, "totalCreditsRequired", 100,
                                "price", Map.of("amount", 100000, "currency", "ARS"), "enrollmentFeeAmount", Map.of("amount", 50000, "currency", "ARS")))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        programId = objectMapper.readTree(response).get("id").asLong();
        priceId = priceIdOf("MONTHLY_INSTALLMENT");
        enrollmentFeePriceId = priceIdOf("ENROLLMENT_FEE");
    }

    private Person person(String prefix, String roleName) {
        return transactionTemplate.execute(status -> {
            String username = prefix + "." + System.nanoTime();
            Person person = new Person();
            person.setUsername(username);
            person.setDni(Long.toString(Math.abs((long) username.hashCode())));
            person.setName("Persona");
            person.setLastname("Prueba");
            person.setEmail(username + "@example.com");
            person.setAddress(addressRepository.save(new com.red_escuela.backend.shared.domain.Address()));
            person.setStatus(StatusType.ACTIVE);
            person.getRoles().add(roleRepository.findByName(roleName).orElseThrow());
            return personRepository.save(person);
        });
    }

    private long priceIdOf(String feeType) {
        return jdbc.queryForObject("select id from price where program_id = ? and fee_type = ?", Long.class, programId, feeType);
    }

    private static String body(long amount, LocalDate effectiveFrom, String reason) {
        return "{\"amount\":{\"amount\":" + amount + ",\"currency\":\"ARS\"},\"effectiveFrom\":\"" + effectiveFrom + "\",\"changeReason\":\"" + reason + "\"}";
    }

    private ResultActions propose(String token, long targetPriceId, long amount, LocalDate effectiveFrom) throws Exception {
        return mockMvc.perform(post("/prices/{id}/versions", targetPriceId).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(body(amount, effectiveFrom, "aumento")));
    }

    private long proposeAccepted(long amount, LocalDate effectiveFrom) throws Exception {
        String response = propose(administrativeToken, priceId, amount, effectiveFrom).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("authorizationRequestId").asLong();
    }

    private ResultActions decide(String token, long requestId, String action, String reasonJson) throws Exception {
        return mockMvc.perform(post("/authorization-requests/{id}/" + action, requestId).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(reasonJson));
    }

    private String versionStatusOf(long requestId) {
        return jdbc.queryForObject("select status from price_version where authorization_request_id = ?", String.class, requestId);
    }

    private long amountOn(LocalDate date) {
        return priceReader.requirePriceVersionEffectiveOn(OfferingReference.ofProgram(programId), FeeType.MONTHLY_INSTALLMENT, date).finalAmount().value().amount().longValue();
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    @Test
    @Tag("D-114")
    @Tag("D-119")
    @Tag("D-117")
    void administrativeProposalLeavesThePriceUntouchedAndNotifiesTheDirector() throws Exception {
        long requestId = proposeAccepted(120000, SEPTEMBER_FIRST);

        assertThat(versionStatusOf(requestId)).isEqualTo("PROPOSED");
        assertThat(jdbc.queryForObject("select proposed_by_id from price_version where authorization_request_id = ?", Long.class, requestId)).isEqualTo(administrative.getId());
        assertThat(jdbc.queryForObject("select status from authorization_request where id = ?", String.class, requestId)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("select required_role from authorization_request where id = ?", String.class, requestId)).isEqualTo("DIRECTOR");
        assertThat(jdbc.queryForObject("select expires_on from authorization_request where id = ?", LocalDate.class, requestId)).isEqualTo(SEPTEMBER_FIRST);
        assertThat(amountOn(TODAY)).isEqualTo(100000);
        assertThat(amountOn(SEPTEMBER_FIRST)).isEqualTo(100000);
        await().until(() -> count("select count(*) from notification where recipient_id = ? and type = 'AUTHORIZATION_REQUESTED' and related_entity_id = ?",
                director.getId(), requestId) >= 1);
    }

    @Test
    @Tag("D-114")
    @Tag("D-119")
    @Tag("D-117")
    void directorApprovalSchedulesTheProposalAndThePriceChangesOnItsStartDate() throws Exception {
        long requestId = proposeAccepted(120000, SEPTEMBER_FIRST);

        decide(directorToken, requestId, "approve", "{}").andExpect(status().isOk());

        assertThat(versionStatusOf(requestId)).isEqualTo("SCHEDULED");
        assertThat(jdbc.queryForObject("select decided_by_id from price_version where authorization_request_id = ?", Long.class, requestId)).isEqualTo(director.getId());
        assertThat(jdbc.queryForObject("select status from authorization_request where id = ?", String.class, requestId)).isEqualTo("APPROVED");
        assertThat(amountOn(SEPTEMBER_FIRST.minusDays(1))).isEqualTo(100000);
        assertThat(amountOn(SEPTEMBER_FIRST)).isEqualTo(120000);
        await().until(() -> count("select count(*) from notification where recipient_id = ? and type = 'PRICE_CHANGE_APPROVED'", administrative.getId()) >= 1);
    }

    @Test
    @Tag("D-114")
    @Tag("D-119")
    void approvingAProposalThatStartsTodayMakesItEffectiveAndSupersedesThePreviousVersion() throws Exception {
        long requestId = proposeAccepted(130000, TODAY);

        decide(directorToken, requestId, "approve", "{}").andExpect(status().isOk());

        assertThat(versionStatusOf(requestId)).isEqualTo("EFFECTIVE");
        assertThat(count("select count(*) from price_version where price_id = ? and status = 'SUPERSEDED'", priceId)).isEqualTo(1);
        assertThat(count("select count(*) from price_version where price_id = ? and status = 'EFFECTIVE'", priceId)).isEqualTo(1);
        assertThat(amountOn(TODAY)).isEqualTo(130000);
    }

    @Test
    @Tag("D-114")
    @Tag("D-117")
    void rejectionNeedsAReasonAndLeavesThePriceFreeForANewProposal() throws Exception {
        long requestId = proposeAccepted(120000, SEPTEMBER_FIRST);

        decide(directorToken, requestId, "reject", "{}").andExpect(status().isBadRequest()).andExpect(content().string(containsString("REJECTION_REASON_REQUIRED")));
        assertThat(versionStatusOf(requestId)).isEqualTo("PROPOSED");
        assertThat(jdbc.queryForObject("select status from authorization_request where id = ?", String.class, requestId)).isEqualTo("PENDING");

        decide(directorToken, requestId, "reject", "{\"decisionReason\":\"aumento excesivo\"}").andExpect(status().isOk());

        assertThat(versionStatusOf(requestId)).isEqualTo("REJECTED");
        assertThat(jdbc.queryForObject("select decision_reason from price_version where authorization_request_id = ?", String.class, requestId)).isEqualTo("aumento excesivo");
        assertThat(amountOn(SEPTEMBER_FIRST)).isEqualTo(100000);
        await().until(() -> count("select count(*) from notification where recipient_id = ? and type = 'PRICE_CHANGE_REJECTED' and message like '%aumento excesivo%'",
                administrative.getId()) >= 1);
        propose(administrativeToken, priceId, 110000, SEPTEMBER_FIRST).andExpect(status().isAccepted());
    }

    @Test
    @Tag("D-225")
    void administrativeWithdrawsOnlyItsOwnPendingProposal() throws Exception {
        long requestId = proposeAccepted(120000, SEPTEMBER_FIRST);
        String otherToken = jwtService.generateAccessToken(person("administrative", "ADMINISTRATIVO"));

        mockMvc.perform(post("/authorization-requests/{id}/withdraw", requestId).header("Authorization", "Bearer " + otherToken)).andExpect(status().isForbidden());
        assertThat(versionStatusOf(requestId)).isEqualTo("PROPOSED");

        mockMvc.perform(post("/authorization-requests/{id}/withdraw", requestId).header("Authorization", "Bearer " + administrativeToken)).andExpect(status().isOk());
        assertThat(versionStatusOf(requestId)).isEqualTo("WITHDRAWN");
        long freed = proposeAccepted(125000, SEPTEMBER_FIRST);
        decide(directorToken, freed, "approve", "{}").andExpect(status().isOk());

        mockMvc.perform(post("/authorization-requests/{id}/withdraw", freed).header("Authorization", "Bearer " + administrativeToken)).andExpect(status().isConflict());
        assertThat(versionStatusOf(freed)).isEqualTo("SCHEDULED");
    }

    @Test
    @Tag("D-116")
    @Tag("D-211")
    void secondProposalOnTheSamePriceIsAConflictButAnotherFeeOfTheSameOfferingIsAccepted() throws Exception {
        proposeAccepted(120000, SEPTEMBER_FIRST);
        long requestsBefore = count("select count(*) from authorization_request where requested_by_id = ?", administrative.getId());

        propose(administrativeToken, priceId, 125000, SEPTEMBER_FIRST.plusDays(5)).andExpect(status().isConflict()).andExpect(content().string(containsString("PROPOSAL_ALREADY_OPEN")));

        assertThat(count("select count(*) from authorization_request where requested_by_id = ?", administrative.getId())).isEqualTo(requestsBefore);
        assertThat(count("select count(*) from price_version where price_id = ? and status = 'PROPOSED'", priceId)).isEqualTo(1);
        propose(administrativeToken, enrollmentFeePriceId, 60000, SEPTEMBER_FIRST).andExpect(status().isAccepted());
    }

    @Test
    @Tag("D-116")
    @Tag("D-096")
    void validationReportsAnOpenProposalToTheAdministrativeButNotToTheDirector() throws Exception {
        proposeAccepted(120000, SEPTEMBER_FIRST);

        mockMvc.perform(post("/prices/{id}/versions/validation", priceId).header("Authorization", "Bearer " + administrativeToken).contentType(MediaType.APPLICATION_JSON)
                        .content(body(130000, SEPTEMBER_FIRST.plusDays(3), "otro")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.violations[0].code").value("PROPOSAL_ALREADY_OPEN"));
        mockMvc.perform(post("/prices/{id}/versions/validation", priceId).header("Authorization", "Bearer " + directorToken).contentType(MediaType.APPLICATION_JSON)
                        .content(body(130000, SEPTEMBER_FIRST.plusDays(3), "otro")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.violations").isEmpty());
    }

    @Test
    @Tag("D-110")
    void onlyDirectorAndAdministrativeMayProposePricesAndOnlyTheDirectorMayDecide() throws Exception {
        for (String role : new String[]{"PRECEPTOR", "DOCENTE", "ALUMNO"}) {
            String token = jwtService.generateAccessToken(person(role.toLowerCase(), role));
            propose(token, priceId, 120000, SEPTEMBER_FIRST).andExpect(status().isForbidden());
            mockMvc.perform(post("/prices/{id}/versions/validation", priceId).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                    .content(body(120000, SEPTEMBER_FIRST, "x"))).andExpect(status().isForbidden());
        }
        long requestId = proposeAccepted(120000, SEPTEMBER_FIRST);

        decide(administrativeToken, requestId, "approve", "{}").andExpect(status().isForbidden());
        decide(administrativeToken, requestId, "reject", "{\"decisionReason\":\"no\"}").andExpect(status().isForbidden());

        assertThat(versionStatusOf(requestId)).isEqualTo("PROPOSED");
    }

    @Test
    @Tag("D-180")
    @Tag("D-116")
    void aProposalNotDecidedBeforeItsStartExpiresNotifiesTheRequesterAndFreesThePrice() throws Exception {
        long requestId = proposeAccepted(120000, SEPTEMBER_FIRST);
        MutableClock.INSTANCE.setDate(SEPTEMBER_FIRST.minusDays(1));
        expirer.expireDueRequests();
        assertThat(versionStatusOf(requestId)).isEqualTo("PROPOSED");

        MutableClock.INSTANCE.setDate(SEPTEMBER_FIRST);
        expirer.expireDueRequests();

        assertThat(versionStatusOf(requestId)).isEqualTo("EXPIRED");
        assertThat(jdbc.queryForObject("select status from authorization_request where id = ?", String.class, requestId)).isEqualTo("EXPIRED");
        assertThat(amountOn(SEPTEMBER_FIRST)).isEqualTo(100000);
        await().until(() -> count("select count(*) from notification where recipient_id = ? and type = 'AUTHORIZATION_REQUEST_EXPIRED' and related_entity_id = ? "
                + "and message like 'Una propuesta de precio%'", administrative.getId(), requestId) >= 1);
        propose(administrativeToken, priceId, 125000, SEPTEMBER_FIRST.plusDays(10)).andExpect(status().isAccepted());
    }

    @Test
    @Tag("D-180")
    void approvingOnTheStartDateBeforeTheExpirationJobIsAConflictAndKeepsThePrice() throws Exception {
        long requestId = proposeAccepted(120000, SEPTEMBER_FIRST);
        MutableClock.INSTANCE.setDate(SEPTEMBER_FIRST);

        decide(directorToken, requestId, "approve", "{}").andExpect(status().isConflict()).andExpect(content().string(containsString("PROPOSAL_EXPIRED")));

        assertThat(amountOn(SEPTEMBER_FIRST)).isEqualTo(100000);
        assertThat(count("select count(*) from price_version where price_id = ? and status in ('SCHEDULED', 'EFFECTIVE') and amount = 120000", priceId)).isZero();
        assertThat(versionStatusOf(requestId)).isEqualTo("REJECTED");
        assertThat(authorizationRequests.findById(requestId).orElseThrow().getResumeOutcome()).isEqualTo(AuthorizationResumeOutcome.REJECTED_ON_RESUME);
        assertThat(jdbc.queryForObject("select decision_reason from price_version where authorization_request_id = ?", String.class, requestId)).startsWith("Rechazada al aprobar: ");
        await().until(() -> count("select count(*) from notification where recipient_id = ? and type = 'PRICE_CHANGE_REJECTED' and message like '%Rechazada al aprobar: %'",
                administrative.getId()) >= 1);
    }

    @Test
    @Tag("D-180")
    @Tag("D-116")
    void approvingARequestWhoseProposalIsNoLongerOpenIsAConflictAndRecordsTheFailedResume() throws Exception {
        long requestId = proposeAccepted(120000, SEPTEMBER_FIRST);
        jdbc.update("update price_version set status = 'WITHDRAWN' where authorization_request_id = ?", requestId);

        decide(directorToken, requestId, "approve", "{}").andExpect(status().isConflict()).andExpect(content().string(containsString("PROPOSAL_NO_LONGER_OPEN")));

        assertThat(authorizationRequests.findById(requestId).orElseThrow().getResumeOutcome()).isEqualTo(AuthorizationResumeOutcome.REJECTED_ON_RESUME);
        assertThat(amountOn(SEPTEMBER_FIRST)).isEqualTo(100000);
    }

    @Test
    @Tag("D-277")
    @Tag("D-114")
    void approvedProposalReplacesAScheduledVersionWithTheSameStartDate() throws Exception {
        long requestId = proposeAccepted(120000, SEPTEMBER_FIRST);
        propose(directorToken, priceId, 115000, SEPTEMBER_FIRST).andExpect(status().isCreated());

        decide(directorToken, requestId, "approve", "{}").andExpect(status().isOk());

        assertThat(versionStatusOf(requestId)).isEqualTo("SCHEDULED");
        assertThat(count("select count(*) from price_version where price_id = ? and status = 'CANCELLED' and amount = 115000", priceId)).isEqualTo(1);
        assertThat(amountOn(SEPTEMBER_FIRST)).isEqualTo(120000);
    }

    @Test
    @Tag("D-114")
    void theDirectorSeesTodayVersusProposedPriceInTheAuthorizationRequest() throws Exception {
        long requestId = proposeAccepted(120000, SEPTEMBER_FIRST);

        mockMvc.perform(get("/authorization-requests/{id}", requestId).header("Authorization", "Bearer " + directorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detailBlock.type").value("CHANGE"))
                .andExpect(jsonPath("$.detailBlock.changeRows[?(@.label == 'Precio final')].today").value("$100.000"))
                .andExpect(jsonPath("$.detailBlock.changeRows[?(@.label == 'Precio final')].proposed").value("$120.000"))
                .andExpect(jsonPath("$.detailBlock.changeRows[?(@.label == 'Precio final')].difference").value("+$20.000"));
    }

    @Test
    @Tag("D-240")
    @Tag("D-114")
    void marketingDiscountsOfProposalsSurviveTheApprovalAndRuleTheFinalAmount() throws Exception {
        String percentage = "{\"amount\":{\"amount\":120000,\"currency\":\"ARS\"},\"effectiveFrom\":\"" + SEPTEMBER_FIRST
                + "\",\"changeReason\":\"aumento\",\"marketingDiscount\":{\"type\":\"PERCENTAGE\",\"percent\":20}}";
        String fixed = "{\"amount\":{\"amount\":60000,\"currency\":\"ARS\"},\"effectiveFrom\":\"" + SEPTEMBER_FIRST
                + "\",\"changeReason\":\"aumento\",\"marketingDiscount\":{\"type\":\"FIXED\",\"fixedAmount\":{\"amount\":10000,\"currency\":\"ARS\"}}}";
        long percentageRequest = requestIdOf(mockMvc.perform(post("/prices/{id}/versions", priceId).header("Authorization", "Bearer " + administrativeToken)
                .contentType(MediaType.APPLICATION_JSON).content(percentage)).andExpect(status().isAccepted()));
        long fixedRequest = requestIdOf(mockMvc.perform(post("/prices/{id}/versions", enrollmentFeePriceId).header("Authorization", "Bearer " + administrativeToken)
                .contentType(MediaType.APPLICATION_JSON).content(fixed)).andExpect(status().isAccepted()));

        decide(directorToken, percentageRequest, "approve", "{}").andExpect(status().isOk());
        decide(directorToken, fixedRequest, "approve", "{}").andExpect(status().isOk());

        assertThat(amountOn(SEPTEMBER_FIRST)).isEqualTo(96000);
        assertThat(priceReader.requirePriceVersionEffectiveOn(OfferingReference.ofProgram(programId), FeeType.ENROLLMENT_FEE, SEPTEMBER_FIRST).finalAmount().value().amount().longValue())
                .isEqualTo(50000);
    }

    @Test
    @Tag("D-280")
    @Tag("D-114")
    void anApprovedProposalCoexistsWithALaterScheduledVersionEachRulingItsOwnStretch() throws Exception {
        LocalDate octoberFirst = LocalDate.of(2027, 10, 1);
        propose(directorToken, priceId, 120000, octoberFirst).andExpect(status().isCreated());
        long requestId = proposeAccepted(110000, SEPTEMBER_FIRST);

        decide(directorToken, requestId, "approve", "{}").andExpect(status().isOk());

        assertThat(amountOn(SEPTEMBER_FIRST)).isEqualTo(110000);
        assertThat(amountOn(octoberFirst.minusDays(1))).isEqualTo(110000);
        assertThat(amountOn(octoberFirst)).isEqualTo(120000);
        assertThat(count("select count(*) from price_version where price_id = ? and status = 'SCHEDULED'", priceId)).isEqualTo(2);
    }

    private long requestIdOf(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("authorizationRequestId").asLong();
    }
}
