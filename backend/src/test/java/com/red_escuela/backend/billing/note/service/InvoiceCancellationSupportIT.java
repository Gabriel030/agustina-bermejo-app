package com.red_escuela.backend.billing.note.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.billing.authorization.domain.PendingInvoiceAuthorization;
import com.red_escuela.backend.billing.authorization.service.PendingInvoiceAuthorizationReader;
import com.red_escuela.backend.testsupport.SlowTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SlowTest(reason = "Runs HTTP cancellation preview, authorship and credit balance scenarios and its time includes the one-time Spring context start-up")
class InvoiceCancellationSupportIT extends AbstractInvoiceCancellationIT {

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PendingInvoiceAuthorizationReader pendingAuthorizationReader;

    private JsonNode preview(Long invoiceId, Staff staff, String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/invoices/" + invoiceId + "/cancellations/preview").header("Authorization", "Bearer " + staff.token())
                .contentType(APPLICATION_JSON).content(body)).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static java.math.BigDecimal amountOf(JsonNode money) {
        return new java.math.BigDecimal(money.get("amount").asText());
    }

    private JsonNode getJson(String path, Staff staff, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(get(path).header("Authorization", "Bearer " + staff.token())).andExpect(status().is(expectedStatus)).andReturn();
        return expectedStatus == 200 ? objectMapper.readTree(result.getResponse().getContentAsString()) : null;
    }

    @Test
    @Tag("D-127")
    @Tag("D-202")
    void thePreviewOfAnUnpaidInstallmentMatchesTheRealCancellationAndWritesNothing() throws Exception {
        Scenario scenario = unpaidInvoice("1000.00");
        Staff director = newStaff("DIRECTOR");
        long notesBefore = creditNoteCountOf(scenario.invoiceId());

        JsonNode preview = preview(scenario.invoiceId(), director, cancellationBody("BILLING_ERROR", "cargo mal emitido"));

        assertThat(creditNoteCountOf(scenario.invoiceId())).isEqualTo(notesBefore);
        assertThat(paymentStatusOf(scenario.invoiceId())).isEqualTo("PENDING");
        assertThat(preview.get("allowed").asBoolean()).isTrue();
        assertThat(preview.get("blockedBy")).isEmpty();
        assertThat(preview.get("releasesChargeKey").asBoolean()).isTrue();
        assertThat(preview.get("executesImmediately").asBoolean()).isTrue();
        assertThat(preview.get("lateFeesToAnnul")).isEmpty();
        assertThat(amountOf(preview.get("creditNoteAmount"))).isEqualByComparingTo("1000.00");

        MvcResult real = requestCancellation(scenario.invoiceId(), director, cancellationBody("BILLING_ERROR", "cargo mal emitido"), 201);
        JsonNode note = objectMapper.readTree(real.getResponse().getContentAsString());
        assertThat(amountOf(note.get("totalAmount"))).isEqualByComparingTo(amountOf(preview.get("creditNoteAmount")));
    }

    @Test
    @Tag("D-202")
    void thePreviewOfACancellationReasonKeepsTheChargeKeyAndAnAdministrativeUserDoesNotExecuteImmediately() throws Exception {
        Scenario scenario = unpaidInvoice("500.00");

        JsonNode preview = preview(scenario.invoiceId(), newStaff("ADMINISTRATIVO"), cancellationBody("CANCELLATION", "baja del servicio"));

        assertThat(preview.get("allowed").asBoolean()).isTrue();
        assertThat(preview.get("releasesChargeKey").asBoolean()).isFalse();
        assertThat(preview.get("executesImmediately").asBoolean()).isFalse();
    }

    @Test
    @Tag("D-127")
    @Tag("D-091")
    void thePreviewExplainsTheSameBlocksTheRealCancellationReturns() throws Exception {
        Scenario paid = unpaidInvoice("800.00");
        payAccount(paid.accountId(), "800.00", LocalDate.of(2027, 3, 5));
        Staff director = newStaff("DIRECTOR");

        JsonNode blockedByPayment = preview(paid.invoiceId(), director, cancellationBody("BILLING_ERROR", "error"));
        JsonNode blockedByJustification = preview(unpaidInvoice("100.00").invoiceId(), director, cancellationBody("BILLING_ERROR", null));

        assertThat(blockedByPayment.get("allowed").asBoolean()).isFalse();
        assertThat(blockedByPayment.get("blockedBy").findValuesAsText("code")).contains("ILLEGAL_INVOICE_PAYMENT_STATUS_TRANSITION");
        requestCancellation(paid.invoiceId(), director, cancellationBody("BILLING_ERROR", "error"), 409);
        assertThat(blockedByJustification.get("allowed").asBoolean()).isFalse();
        assertThat(blockedByJustification.get("blockedBy").findValuesAsText("code")).contains("CREDIT_NOTE_JUSTIFICATION_REQUIRED");
    }

    @Test
    @Tag("D-127")
    void onlyAdministrativeAndDirectorUsersCanPreviewACancellation() throws Exception {
        Scenario scenario = unpaidInvoice("100.00");
        Staff student = studentStaff(scenario.studentId());

        mockMvc.perform(post("/invoices/" + scenario.invoiceId() + "/cancellations/preview").header("Authorization", "Bearer " + student.token())
                .contentType(APPLICATION_JSON).content(cancellationBody("BILLING_ERROR", "x"))).andExpect(status().isForbidden());
        mockMvc.perform(post("/invoices/" + scenario.invoiceId() + "/cancellations/preview").contentType(APPLICATION_JSON)
                .content(cancellationBody("BILLING_ERROR", "x"))).andExpect(status().isForbidden());
    }

    @Test
    @Tag("D-165")
    void aPendingCancellationRequestIsExposedForTheInvoiceUntilItIsDecided() throws Exception {
        Scenario scenario = unpaidInvoice("700.00");
        Staff administrative = newStaff("ADMINISTRATIVO");
        Staff director = newStaff("DIRECTOR");

        assertThat(pendingAuthorizationReader.findPendingByInvoiceIdNewestFirst(List.of(scenario.invoiceId()))).isEmpty();
        long cancellationId = pendingRequestId(requestCancellation(scenario.invoiceId(), administrative, cancellationBody("CANCELLATION", "baja"), 202));
        long creditNoteId = pendingRequestId(requestCreditNote(scenario.invoiceId(), administrative, creditNoteBody("BILLING_ERROR", "monto de mas", 1, "100.00"), 202));

        Map<Long, List<PendingInvoiceAuthorization>> pending = pendingAuthorizationReader.findPendingByInvoiceIdNewestFirst(List.of(scenario.invoiceId()));

        List<PendingInvoiceAuthorization> ofInvoice = pending.get(scenario.invoiceId());
        assertThat(ofInvoice).extracting(PendingInvoiceAuthorization::id).containsExactly(creditNoteId, cancellationId);
        assertThat(ofInvoice).extracting(entry -> entry.type().name()).containsExactly("ISSUE_CREDIT_NOTE", "CANCEL_INVOICE");
        assertThat(ofInvoice.get(1).requestedByPersonId()).isEqualTo(administrative.personId());
        assertThat(ofInvoice.get(1).requestedByName()).isNotBlank();
        decide("reject", creditNoteId, director, 200);
        assertThat(pendingAuthorizationReader.findPendingByInvoiceIdNewestFirst(List.of(scenario.invoiceId())).get(scenario.invoiceId()))
                .extracting(PendingInvoiceAuthorization::id).containsExactly(cancellationId);
        decide("reject", cancellationId, director, 200);
        assertThat(pendingAuthorizationReader.findPendingByInvoiceIdNewestFirst(List.of(scenario.invoiceId()))).isEmpty();
    }

    @Test
    @Tag("D-165")
    void theStaffListShowsWhoRequestedAndWhoAuthorizedTheCreditNoteButTheStudentListHidesStaffNames() throws Exception {
        Scenario scenario = unpaidInvoice("900.00");
        Staff administrative = newStaff("ADMINISTRATIVO");
        Staff director = newStaff("DIRECTOR");
        jdbc.update("update person set name = 'Requester', last_name = 'Person' where id = ?", administrative.personId());
        jdbc.update("update person set name = 'Authorizer', last_name = 'Person' where id = ?", director.personId());
        long requestId = pendingRequestId(requestCancellation(scenario.invoiceId(), administrative, cancellationBody("CANCELLATION", "baja"), 202));
        decide("approve", requestId, director, 200);

        JsonNode staffNotes = getJson("/students/" + scenario.studentId() + "/credit-notes", director, 200);
        JsonNode studentNotes = getJson("/me/credit-notes", studentStaff(scenario.studentId()), 200);

        assertThat(staffNotes).hasSize(1);
        assertThat(staffNotes.get(0).get("requestedByName").asText()).isNotBlank();
        assertThat(staffNotes.get(0).get("authorizedByName").asText()).isNotBlank();
        assertThat(staffNotes.get(0).get("requestedByName").asText()).contains("Requester");
        assertThat(staffNotes.get(0).get("authorizedByName").asText()).contains("Authorizer");
        assertThat(staffNotes.get(0).get("authorizedAt").isNull()).isFalse();
        assertThat(staffNotes.get(0).get("authorizationRequestId").asLong()).isEqualTo(requestId);
        assertThat(studentNotes).hasSize(1);
        assertThat(staffNotes.get(0).get("justification").asText()).isEqualTo("baja");
        assertThat(studentNotes.get(0).get("justification").isNull()).isTrue();
        assertThat(studentNotes.get(0).get("requestedByName").isNull()).isTrue();
        assertThat(studentNotes.get(0).get("authorizedByName").isNull()).isTrue();
    }

    @Test
    @Tag("D-165")
    void aCreditNoteIssuedDirectlyByTheDirectorHasARequesterAndNoAuthorizer() throws Exception {
        Scenario scenario = unpaidInvoice("900.00");
        Staff director = newStaff("DIRECTOR");
        requestCancellation(scenario.invoiceId(), director, cancellationBody("CANCELLATION", "baja"), 201);

        JsonNode notes = getJson("/students/" + scenario.studentId() + "/credit-notes", director, 200);

        assertThat(notes.get(0).get("requestedByName").asText()).isNotBlank();
        assertThat(notes.get(0).get("authorizedByName").isNull()).isTrue();
        assertThat(notes.get(0).get("authorizedAt").isNull()).isTrue();
    }

    @Test
    @Tag("D-159")
    void theCreditBalanceShowsUnallocatedReservedAndAvailableCreditAndIsRestrictedByRole() throws Exception {
        Scenario scenario = unpaidInvoice("100.00");
        payAccount(scenario.accountId(), "100.00", LocalDate.of(2027, 3, 5));
        payAccount(scenario.accountId(), "5000.00", LocalDate.of(2027, 3, 6));
        Staff administrative = newStaff("ADMINISTRATIVO");
        Staff student = studentStaff(scenario.studentId());

        JsonNode balance = getJson("/students/" + scenario.studentId() + "/credit-balance", administrative, 200);
        JsonNode ownBalance = getJson("/me/credit-balance", student, 200);

        assertThat(amountOf(balance.get("unallocatedCredit"))).isEqualByComparingTo("5000.00");
        assertThat(amountOf(balance.get("reservedByPendingRefunds"))).isEqualByComparingTo("0");
        assertThat(amountOf(balance.get("availableCredit"))).isEqualByComparingTo("5000.00");
        assertThat(amountOf(ownBalance.get("availableCredit"))).isEqualByComparingTo("5000.00");
        getJson("/students/" + scenario.studentId() + "/credit-balance", student, 403);
        getJson("/me/credit-balance", administrative, 403);
        getJson("/students/999999999/credit-balance", administrative, 404);
    }
}
