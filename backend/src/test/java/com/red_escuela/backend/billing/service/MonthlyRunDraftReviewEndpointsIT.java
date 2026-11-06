package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.entity.CohortIntake;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceIssuanceMode;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRun;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunStatus;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunTrigger;
import com.red_escuela.backend.billing.run.repository.MonthlyInvoiceRunItemRepository;
import com.red_escuela.backend.billing.run.repository.MonthlyInvoiceRunRepository;
import com.red_escuela.backend.billing.run.service.MonthlyInvoiceRunExecutor;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.infrastructure.JwtService;
import com.red_escuela.backend.identity.repository.RoleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@Tag("D-518")
@Tag("D-110")
class MonthlyRunDraftReviewEndpointsIT extends AbstractInvoiceSequenceScenarioTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private MonthlyInvoiceRunExecutor executor;
    @Autowired
    private MonthlyInvoiceRunRepository runRepository;
    @Autowired
    private MonthlyInvoiceRunItemRepository itemRepository;

    private String administrative;
    private String preceptor;

    @BeforeEach
    @AfterEach
    void forgetRunsAndDetachTheirInvoices() {
        jdbcTemplate.update("update invoice set monthly_invoice_run_id = null where monthly_invoice_run_id is not null");
        jdbcTemplate.update("delete from monthly_invoice_run_item");
        jdbcTemplate.update("delete from monthly_invoice_run");
    }

    @BeforeEach
    void issueStaffTokens() {
        administrative = tokenWithRole("ADMINISTRATIVO");
        preceptor = tokenWithRole("PRECEPTOR");
    }

    private String tokenWithRole(String role) {
        return transactionTemplate.execute(status -> {
            Person person = newPerson();
            person.getRoles().add(roleRepository.findByName(role).orElseThrow());
            return jwtService.generateAccessToken(person);
        });
    }

    private MonthlyInvoiceRun reviewRunWithDrafts(int enrollments) {
        var career = newCareer();
        var year = newYear(career, 2026, 10, 1, 2, null);
        Long cohort = newCohort(year, CohortIntake.FIRST, date(2026, 3, 1));
        today(date(2026, 4, 1));
        for (int i = 0; i < enrollments; i++) {
            newEnrollment(career, cohort, PaymentPlanCadence.MONTHLY, YearMonth.of(2026, 4), date(2026, 3, 10));
        }
        job.completeTheNightOf(date(2026, 4, 1));
        return executor.runMonthlyInvoicesOfPeriodPricedAt(YearMonth.of(2026, 4), date(2026, 4, 1), date(2026, 4, 1), MonthlyInvoiceRunTrigger.SCHEDULED,
                MonthlyInvoiceIssuanceMode.REVIEW).orElseThrow();
    }

    private List<Long> draftIdsOf(MonthlyInvoiceRun run) {
        return itemRepository.findByMonthlyInvoiceRunIdOrderByIdAsc(run.getId()).stream().flatMap(item -> item.getInvoiceIds().stream()).sorted().toList();
    }

    private String documentStatusOf(Long invoiceId) {
        return jdbcTemplate.queryForObject("select document_status from invoice where id = ?", String.class, invoiceId);
    }

    private String idsBody(List<Long> ids) {
        return "{\"invoiceIds\":" + ids + "}";
    }

    private MvcResult approve(Long runId, List<Long> ids, String key, String token) throws Exception {
        var request = post("/monthly-invoice-runs/" + runId + "/draft-invoices/approvals").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(idsBody(ids));
        return mockMvc.perform(key == null ? request : request.header("Idempotency-Key", key)).andReturn();
    }

    private MvcResult discard(Long runId, List<Long> ids, String reason, String token) throws Exception {
        String body = "{\"invoiceIds\":" + ids + ",\"reason\":" + mapper.writeValueAsString(reason) + "}";
        return mockMvc.perform(post("/monthly-invoice-runs/" + runId + "/draft-invoices/discards").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    private JsonNode jsonOf(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private String key() {
        return UUID.randomUUID().toString();
    }

    @Test
    void theDraftGroupListingExposesTheInvoiceIdsOfEachGroup() throws Exception {
        MonthlyInvoiceRun run = reviewRunWithDrafts(3);

        JsonNode groups = jsonOf(mockMvc.perform(get("/monthly-invoice-runs/" + run.getId() + "/draft-invoices").header("Authorization", "Bearer " + administrative)).andReturn());

        List<Long> listed = groups.get(0).get("invoiceIds").valueStream().map(JsonNode::asLong).sorted().toList();
        assertThat(listed).isEqualTo(draftIdsOf(run));
        assertThat(groups.get(0).get("draftCount").asInt()).isEqualTo(listed.size());
    }

    @Test
    void approvingTheListedIdsIssuesOnlyThemAndCreatesNoAuthorizationRequest() throws Exception {
        MonthlyInvoiceRun run = reviewRunWithDrafts(3);
        List<Long> ids = draftIdsOf(run);
        today(date(2026, 4, 5));

        MvcResult result = approve(run.getId(), ids.subList(0, 2), key(), administrative);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(jsonOf(result).get("count").asInt()).isEqualTo(2);
        assertThat(documentStatusOf(ids.get(0))).isEqualTo("ISSUED");
        assertThat(documentStatusOf(ids.get(1))).isEqualTo("ISSUED");
        assertThat(documentStatusOf(ids.get(2))).isEqualTo("DRAFT");
        assertThat(jdbcTemplate.queryForObject("select count(*) from authorization_request", Long.class)).isZero();
        assertThat(runRepository.findById(run.getId()).orElseThrow().getStatus()).isEqualTo(MonthlyInvoiceRunStatus.AWAITING_REVIEW);
    }

    @Test
    void retryingAnApprovalWithTheSameKeyReplaysTheStoredAnswerAndIssuesNothingTwice() throws Exception {
        MonthlyInvoiceRun run = reviewRunWithDrafts(2);
        List<Long> ids = draftIdsOf(run);
        today(date(2026, 4, 5));
        String key = key();

        MvcResult first = approve(run.getId(), ids, key, administrative);
        List<String> numbers = jdbcTemplate.queryForList("select invoice_number from invoice where id in (" + ids.get(0) + "," + ids.get(1) + ") order by id", String.class);
        today(date(2026, 4, 9));
        MvcResult retry = approve(run.getId(), ids, key, administrative);

        assertThat(first.getResponse().getStatus()).isEqualTo(200);
        assertThat(retry.getResponse().getStatus()).isEqualTo(200);
        assertThat(jsonOf(retry)).isEqualTo(jsonOf(first));
        assertThat(jdbcTemplate.queryForList("select invoice_number from invoice where id in (" + ids.get(0) + "," + ids.get(1) + ") order by id", String.class)).isEqualTo(numbers);
        assertThat(runRepository.findById(run.getId()).orElseThrow().getStatus()).isEqualTo(MonthlyInvoiceRunStatus.COMPLETED);
    }

    @Test
    void theSameKeyWithAnotherListOfIdsIsAConflict() throws Exception {
        MonthlyInvoiceRun run = reviewRunWithDrafts(3);
        List<Long> ids = draftIdsOf(run);
        today(date(2026, 4, 5));
        String key = key();
        approve(run.getId(), ids.subList(0, 1), key, administrative);

        MvcResult reuse = approve(run.getId(), ids.subList(1, 3), key, administrative);

        assertThat(reuse.getResponse().getStatus()).isEqualTo(409);
        assertThat(jsonOf(reuse).get("code").asString("")).isEqualTo("IDEMPOTENCY_KEY_CONFLICT");
        assertThat(documentStatusOf(ids.get(1))).isEqualTo("DRAFT");
    }

    @Test
    void anApprovalWithoutIdempotencyKeyOrWithoutIdsIsRejected() throws Exception {
        MonthlyInvoiceRun run = reviewRunWithDrafts(1);
        List<Long> ids = draftIdsOf(run);

        assertThat(approve(run.getId(), ids, null, administrative).getResponse().getStatus()).isEqualTo(400);
        assertThat(approve(run.getId(), List.of(), key(), administrative).getResponse().getStatus()).isEqualTo(400);
        assertThat(documentStatusOf(ids.get(0))).isEqualTo("DRAFT");
    }

    @Test
    void aBatchWithADiscardedIdFailsAsAWholeWithDraftNotPending() throws Exception {
        MonthlyInvoiceRun run = reviewRunWithDrafts(3);
        List<Long> ids = draftIdsOf(run);
        today(date(2026, 4, 5));
        assertThat(discard(run.getId(), ids.subList(2, 3), "Cargo duplicado", administrative).getResponse().getStatus()).isEqualTo(200);

        MvcResult result = approve(run.getId(), ids, key(), administrative);

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(jsonOf(result).get("code").asString("")).isEqualTo("DRAFT_NOT_PENDING");
        assertThat(documentStatusOf(ids.get(0))).isEqualTo("DRAFT");
        assertThat(documentStatusOf(ids.get(1))).isEqualTo("DRAFT");
    }

    @Test
    void discardingTheLastDraftsRequiresAReasonAndCompletesTheRun() throws Exception {
        MonthlyInvoiceRun run = reviewRunWithDrafts(2);
        List<Long> ids = draftIdsOf(run);
        today(date(2026, 4, 5));

        assertThat(discard(run.getId(), ids, " ", administrative).getResponse().getStatus()).isEqualTo(400);
        MvcResult result = discard(run.getId(), ids, "Alta por error", administrative);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(documentStatusOf(ids.get(0))).isEqualTo("DISCARDED");
        assertThat(documentStatusOf(ids.get(1))).isEqualTo("DISCARDED");
        assertThat(runRepository.findById(run.getId()).orElseThrow().getStatus()).isEqualTo(MonthlyInvoiceRunStatus.COMPLETED);
    }

    @Test
    void reviewingAFinishedRunIsAConflict() throws Exception {
        MonthlyInvoiceRun run = reviewRunWithDrafts(1);
        List<Long> ids = draftIdsOf(run);
        today(date(2026, 4, 5));
        discard(run.getId(), ids, "Alta por error", administrative);

        MvcResult approval = approve(run.getId(), ids, key(), administrative);
        MvcResult discard = discard(run.getId(), ids, "Otra vez", administrative);

        assertThat(approval.getResponse().getStatus()).isEqualTo(409);
        assertThat(jsonOf(approval).get("code").asString("")).isEqualTo("MONTHLY_INVOICE_RUN_NOT_AWAITING_REVIEW");
        assertThat(discard.getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    void aPreceptorCannotApproveOrDiscardDrafts() throws Exception {
        MonthlyInvoiceRun run = reviewRunWithDrafts(1);
        List<Long> ids = draftIdsOf(run);

        assertThat(approve(run.getId(), ids, key(), preceptor).getResponse().getStatus()).isEqualTo(403);
        assertThat(discard(run.getId(), ids, "Motivo", preceptor).getResponse().getStatus()).isEqualTo(403);
        assertThat(documentStatusOf(ids.get(0))).isEqualTo("DRAFT");
    }
}
