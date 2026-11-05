package com.red_escuela.backend.billing.note.service;

import com.jayway.jsonpath.JsonPath;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.testsupport.SlowTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@Tag("D-092")
@Tag("D-127")
@SlowTest(reason = "Retries credit note requests sequentially and from two real threads against the database")
class CreditNoteIdempotencyIT extends AbstractCreditNoteIT {

    private record Scenario(Long studentId, Long accountId, Long invoiceId) {
    }

    private Scenario unpaidInvoice(String amount) {
        Long studentId = newStudentId();
        Long accountId = newAccountId(studentId, Currency.ARS);
        return new Scenario(studentId, accountId, issueInvoiceOfStudent(studentId, accountId, amount, LocalDate.of(2027, 3, 10)));
    }

    private MockHttpServletRequestBuilder request(Long invoiceId, Staff staff, String key, String amount) {
        MockHttpServletRequestBuilder builder = post("/invoices/" + invoiceId + "/credit-notes").header("Authorization", "Bearer " + staff.token())
                .contentType(MediaType.APPLICATION_JSON).content(creditNoteBody("BILLING_ERROR", "Error", 1, amount));
        return key == null ? builder : builder.header("Idempotency-Key", key);
    }

    private long creditNotesOf(Long invoiceId) {
        return jdbc.queryForObject("select count(*) from credit_note where invoice_id = ?", Long.class, invoiceId);
    }

    private String field(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path).toString();
    }

    @Test
    void m2a01RetryWithTheSameKeyReturnsTheSameCreditNoteWithoutIssuingAnother() throws Exception {
        Scenario scenario = unpaidInvoice("80000.00");
        Staff director = newStaff("DIRECTOR");
        assertThat(creditNotesOf(scenario.invoiceId())).isZero();

        MvcResult first = mockMvc.perform(request(scenario.invoiceId(), director, "key-a01", "30000.00")).andReturn();
        MvcResult retry = mockMvc.perform(request(scenario.invoiceId(), director, "key-a01", "30000.00")).andReturn();

        assertThat(first.getResponse().getStatus()).isEqualTo(201);
        assertThat(retry.getResponse().getStatus()).isEqualTo(201);
        assertThat(field(retry, "$.id")).isEqualTo(field(first, "$.id"));
        assertThat(field(retry, "$.creditNoteNumber")).isEqualTo(field(first, "$.creditNoteNumber"));
        assertThat(field(retry, "$.lines.length()")).isEqualTo(field(first, "$.lines.length()")).isEqualTo("1");
        assertThat(creditNotesOf(scenario.invoiceId())).isEqualTo(1L);
        assertThat(outstandingOf(scenario.invoiceId())).isEqualTo("50000.00");
    }

    @Test
    void m2a02RetryOfAnAmountThatWouldNowExceedTheCreditableLimitStillReplays() throws Exception {
        Scenario scenario = unpaidInvoice("80000.00");
        Staff director = newStaff("DIRECTOR");

        MvcResult first = mockMvc.perform(request(scenario.invoiceId(), director, "key-a02", "50000.00")).andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(201)).andReturn();
        MvcResult retry = mockMvc.perform(request(scenario.invoiceId(), director, "key-a02", "50000.00")).andReturn();
        MvcResult otherKey = mockMvc.perform(request(scenario.invoiceId(), director, "key-a02-other", "50000.00")).andReturn();

        assertThat(retry.getResponse().getStatus()).isEqualTo(201);
        assertThat(field(retry, "$.id")).isEqualTo(field(first, "$.id"));
        assertThat(otherKey.getResponse().getStatus()).isEqualTo(409);
        assertThat(creditNotesOf(scenario.invoiceId())).isEqualTo(1L);
    }

    @Test
    void m2a03TheSameKeyWithAnotherBodyIsAStableConflictAndIssuesNothing() throws Exception {
        Scenario scenario = unpaidInvoice("80000.00");
        Staff director = newStaff("DIRECTOR");
        mockMvc.perform(request(scenario.invoiceId(), director, "key-a03", "10000.00")).andReturn();

        MvcResult conflict = mockMvc.perform(request(scenario.invoiceId(), director, "key-a03", "20000.00")).andReturn();

        assertThat(conflict.getResponse().getStatus()).isEqualTo(409);
        assertThat(field(conflict, "$.code")).isEqualTo("IDEMPOTENCY_KEY_CONFLICT");
        assertThat(creditNotesOf(scenario.invoiceId())).isEqualTo(1L);
        assertThat(outstandingOf(scenario.invoiceId())).isEqualTo("70000.00");
    }

    @Test
    void m2a04WithoutAKeyEveryRequestIssuesItsOwnCreditNoteAsBefore() throws Exception {
        Scenario scenario = unpaidInvoice("80000.00");
        Staff director = newStaff("DIRECTOR");

        MvcResult first = mockMvc.perform(request(scenario.invoiceId(), director, null, "10000.00")).andReturn();
        MvcResult second = mockMvc.perform(request(scenario.invoiceId(), director, null, "10000.00")).andReturn();

        assertThat(first.getResponse().getStatus()).isEqualTo(201);
        assertThat(second.getResponse().getStatus()).isEqualTo(201);
        assertThat(field(second, "$.id")).isNotEqualTo(field(first, "$.id"));
        assertThat(creditNotesOf(scenario.invoiceId())).isEqualTo(2L);
    }

    @Test
    void m2a05AnInvalidKeyIsRejectedBeforeAnythingIsWritten() throws Exception {
        Scenario scenario = unpaidInvoice("80000.00");
        Staff director = newStaff("DIRECTOR");

        MvcResult blank = mockMvc.perform(request(scenario.invoiceId(), director, " ", "10000.00")).andReturn();
        MvcResult tooLong = mockMvc.perform(request(scenario.invoiceId(), director, "k".repeat(101), "10000.00")).andReturn();
        MvcResult maximum = mockMvc.perform(request(scenario.invoiceId(), director, "k".repeat(100), "10000.00")).andReturn();

        assertThat(blank.getResponse().getStatus()).isEqualTo(400);
        assertThat(field(blank, "$.code")).isEqualTo("IDEMPOTENCY_KEY_INVALID");
        assertThat(tooLong.getResponse().getStatus()).isEqualTo(400);
        assertThat(maximum.getResponse().getStatus()).isEqualTo(201);
        assertThat(creditNotesOf(scenario.invoiceId())).isEqualTo(1L);
    }

    @Test
    void m2a06TwoThreadsWithTheSameKeyIssueExactlyOneCreditNote() throws Exception {
        Scenario scenario = unpaidInvoice("80000.00");
        Staff director = newStaff("DIRECTOR");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<MvcResult> call = () -> {
                start.await();
                return mockMvc.perform(request(scenario.invoiceId(), director, "key-a06", "30000.00")).andReturn();
            };
            Future<MvcResult> first = executor.submit(call);
            Future<MvcResult> second = executor.submit(call);
            start.countDown();
            MvcResult one = first.get(60, TimeUnit.SECONDS);
            MvcResult two = second.get(60, TimeUnit.SECONDS);

            assertThat(List.of(one.getResponse().getStatus(), two.getResponse().getStatus())).containsExactly(201, 201);
            assertThat(field(one, "$.id")).isEqualTo(field(two, "$.id"));
        } finally {
            executor.shutdownNow();
        }
        assertThat(creditNotesOf(scenario.invoiceId())).isEqualTo(1L);
        assertThat(outstandingOf(scenario.invoiceId())).isEqualTo("50000.00");
    }

    @Test
    void m2a07AnAdministrativeRetryReturnsTheSamePendingRequestAndTheDirectorApprovalIssuesOneCreditNote() throws Exception {
        Scenario scenario = unpaidInvoice("80000.00");
        Staff administrative = newStaff("ADMINISTRATIVO");
        Staff director = newStaff("DIRECTOR");

        MvcResult first = mockMvc.perform(request(scenario.invoiceId(), administrative, "key-a07", "30000.00")).andReturn();
        MvcResult retry = mockMvc.perform(request(scenario.invoiceId(), administrative, "key-a07", "30000.00")).andReturn();

        assertThat(first.getResponse().getStatus()).isEqualTo(202);
        assertThat(retry.getResponse().getStatus()).isEqualTo(202);
        assertThat(field(retry, "$.authorizationRequestId")).isEqualTo(field(first, "$.authorizationRequestId"));
        assertThat(jdbc.queryForObject("select count(*) from authorization_request where operation_type = 'ISSUE_CREDIT_NOTE' and id = ?", Long.class,
                Long.parseLong(field(first, "$.authorizationRequestId")))).isEqualTo(1L);
        assertThat(creditNotesOf(scenario.invoiceId())).isZero();

        mockMvc.perform(post("/authorization-requests/" + field(first, "$.authorizationRequestId") + "/approve").header("Authorization", "Bearer " + director.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"decisionReason\":\"ok\"}")).andReturn();

        assertThat(creditNotesOf(scenario.invoiceId())).isEqualTo(1L);
        assertThat(outstandingOf(scenario.invoiceId())).isEqualTo("50000.00");
        assertThat(jdbc.queryForObject("select response_status from idempotency_record where idempotency_key = 'key-a07'", Integer.class)).isEqualTo(201);
    }
}
