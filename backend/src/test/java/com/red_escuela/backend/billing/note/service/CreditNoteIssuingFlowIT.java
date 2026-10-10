package com.red_escuela.backend.billing.note.service;

import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.notification.repository.NotificationRepository;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@Tag("D-165")
@Tag("D-216")
class CreditNoteIssuingFlowIT extends AbstractCreditNoteIT {

    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private com.red_escuela.backend.billing.authorization.service.AuthorizationRequestExpirer authorizationRequestExpirer;

    private record Scenario(Long studentId, Long accountId, Long invoiceId) {
    }

    @AfterEach
    void resetClock() {
        MutableClock.INSTANCE.reset();
    }

    private Scenario unpaidInvoice(String amount) {
        Long studentId = newStudentId();
        Long accountId = newAccountId(studentId, Currency.ARS);
        return new Scenario(studentId, accountId, issueInvoiceOfStudent(studentId, accountId, amount, LocalDate.of(2027, 3, 10)));
    }

    private MvcResult requestCreditNote(Long invoiceId, Staff staff, String body, int expectedStatus) throws Exception {
        return mockMvc.perform(post("/invoices/" + invoiceId + "/credit-notes").header("Authorization", "Bearer " + staff.token())
                .contentType(APPLICATION_JSON).content(body)).andExpect(status().is(expectedStatus)).andReturn();
    }

    private long pendingRequestId(MvcResult result) throws Exception {
        return Long.parseLong(com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.authorizationRequestId").toString());
    }

    private void decide(String action, long requestId, Staff staff, int expectedStatus) throws Exception {
        mockMvc.perform(post("/authorization-requests/" + requestId + "/" + action).header("Authorization", "Bearer " + staff.token())
                .contentType(APPLICATION_JSON).content("{\"decisionReason\":\"ok\"}")).andExpect(status().is(expectedStatus));
    }

    private long notificationsOfType(Long personId) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(personId).stream()
                .filter(notification -> notification.getType() == NotificationType.CREDIT_NOTE_ISSUED).count();
    }

    @Test
    @Tag("D-184")
    @Tag("D-244")
    void anAdministrativeRequestWaitsForTheDirectorAndTheApprovalIssuesTheCreditNote() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Scenario scenario = unpaidInvoice("80000.00");
        Staff administrative = newStaff("ADMINISTRATIVO");
        Staff director = newStaff("DIRECTOR");
        long entriesBefore = ledgerEntryCount(scenario.accountId());
        long notesBefore = creditNoteCount();

        MvcResult pending = requestCreditNote(scenario.invoiceId(), administrative, creditNoteBody("BILLING_ERROR", "Error de carga", 1, "30000.00"), 202);

        long requestId = pendingRequestId(pending);
        assertThat(jdbc.queryForObject("select status from authorization_request where id = ?", String.class, requestId)).isEqualTo("PENDING");
        assertThat(creditNoteCount()).isEqualTo(notesBefore);
        assertThat(ledgerEntryCount(scenario.accountId())).isEqualTo(entriesBefore);
        assertThat(outstandingOf(scenario.invoiceId())).isEqualTo("80000.00");

        LocalDate approvalDay = LocalDate.of(2027, 3, 12);
        MutableClock.INSTANCE.setDate(approvalDay);
        decide("approve", requestId, director, 200);

        assertThat(creditNoteCount()).isEqualTo(notesBefore + 1);
        assertThat(outstandingOf(scenario.invoiceId())).isEqualTo("50000.00");
        assertThat(jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, scenario.invoiceId())).isEqualTo("PENDING");
        assertThat(jdbc.queryForMap("select credit_note_number, total_amount, authorization_request_id, fact_date::text as fact_date, issue_date::text as issue_date, "
                + "invoice_number, recipient_snapshot ->> 'documentNumber' as recipient from credit_note where invoice_id = ?", scenario.invoiceId()))
                .containsEntry("authorization_request_id", requestId)
                .containsEntry("fact_date", "2027-03-12")
                .containsEntry("issue_date", "2027-03-12")
                .satisfies(row -> assertThat(row.get("credit_note_number").toString()).matches("NC-\\d{8}"))
                .satisfies(row -> assertThat(row.get("invoice_number")).isNotNull())
                .satisfies(row -> assertThat(row.get("recipient")).isNotNull());
        assertThat(jdbc.queryForObject("select count(*) from current_account_entry where student_current_account_id = ? and entry_type = 'CREDIT_NOTE_ISSUED' and amount = 30000.00",
                Long.class, scenario.accountId())).isEqualTo(1L);
        assertThat(amount("select balance_amount from student_current_account where id = ?", scenario.accountId())).isEqualByComparingTo("50000.00");
        Long studentPersonId = studentRepository.findById(scenario.studentId()).orElseThrow().getPerson().getId();
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(notificationsOfType(studentPersonId)).isEqualTo(1L);
            assertThat(notificationsOfType(administrative.personId())).isEqualTo(1L);
        });
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void theDirectorIssuesTheCreditNoteDirectlyAndItIsListedForTheStudent() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Scenario scenario = unpaidInvoice("80000.00");
        Staff director = newStaff("DIRECTOR");

        requestCreditNote(scenario.invoiceId(), director, creditNoteBody("BILLING_ERROR", "Error de carga", 1, "30000.00"), 201);

        assertThat(jdbc.queryForObject("select authorization_request_id from credit_note where invoice_id = ?", Long.class, scenario.invoiceId())).isNull();
        assertThat(jdbc.queryForObject("select issued_by_person_id from credit_note where invoice_id = ?", Long.class, scenario.invoiceId())).isEqualTo(director.personId());
        mockMvc.perform(get("/students/" + scenario.studentId() + "/credit-notes").header("Authorization", "Bearer " + director.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].invoiceId").value(scenario.invoiceId()))
                .andExpect(jsonPath("$[0].reason").value("BILLING_ERROR"))
                .andExpect(jsonPath("$[0].lines.length()").value(1))
                .andExpect(jsonPath("$[0].authorizationRequestId").doesNotExist());
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void approvingAfterTheInvoiceWasPaidRejectsTheResumeAndLeavesNoCreditNote() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Scenario scenario = unpaidInvoice("80000.00");
        Staff administrative = newStaff("ADMINISTRATIVO");
        Staff director = newStaff("DIRECTOR");
        long requestId = pendingRequestId(requestCreditNote(scenario.invoiceId(), administrative, creditNoteBody("BILLING_ERROR", "Error", 1, "30000.00"), 202));
        payAccount(scenario.accountId(), "80000.00", LocalDate.of(2027, 3, 11));
        long entriesBefore = ledgerEntryCount(scenario.accountId());

        decide("approve", requestId, director, 409);

        assertThat(jdbc.queryForObject("select count(*) from credit_note where invoice_id = ?", Long.class, scenario.invoiceId())).isZero();
        assertThat(ledgerEntryCount(scenario.accountId())).isEqualTo(entriesBefore);
        assertThat(jdbc.queryForMap("select status, resume_outcome from authorization_request where id = ?", requestId))
                .containsEntry("status", "APPROVED").containsEntry("resume_outcome", "REJECTED_ON_RESUME");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void rejectedAndWithdrawnRequestsLeaveNoTraceAndOnlyTheRequesterCanWithdraw() throws Exception {
        Scenario scenario = unpaidInvoice("80000.00");
        Staff administrative = newStaff("ADMINISTRATIVO");
        Staff otherAdministrative = newStaff("ADMINISTRATIVO");
        Staff director = newStaff("DIRECTOR");
        long entriesBefore = ledgerEntryCount(scenario.accountId());
        long toReject = pendingRequestId(requestCreditNote(scenario.invoiceId(), administrative, creditNoteBody("BILLING_ERROR", "Error", 1, "1000.00"), 202));
        long toWithdraw = pendingRequestId(requestCreditNote(scenario.invoiceId(), administrative, creditNoteBody("BILLING_ERROR", "Error", 1, "2000.00"), 202));

        decide("reject", toReject, director, 200);
        decide("withdraw", toWithdraw, otherAdministrative, 403);
        decide("withdraw", toWithdraw, administrative, 200);

        assertThat(jdbc.queryForObject("select status from authorization_request where id = ?", String.class, toReject)).isEqualTo("REJECTED");
        assertThat(jdbc.queryForObject("select status from authorization_request where id = ?", String.class, toWithdraw)).isEqualTo("WITHDRAWN");
        assertThat(jdbc.queryForObject("select count(*) from credit_note where invoice_id = ?", Long.class, scenario.invoiceId())).isZero();
        assertThat(ledgerEntryCount(scenario.accountId())).isEqualTo(entriesBefore);
        assertThat(outstandingOf(scenario.invoiceId())).isEqualTo("80000.00");
    }

    @Test
    void approvingTheSameRequestTwiceIssuesASingleCreditNote() throws Exception {
        Scenario scenario = unpaidInvoice("80000.00");
        Staff administrative = newStaff("ADMINISTRATIVO");
        Staff director = newStaff("DIRECTOR");
        long requestId = pendingRequestId(requestCreditNote(scenario.invoiceId(), administrative, creditNoteBody("BILLING_ERROR", "Error", 1, "30000.00"), 202));

        decide("approve", requestId, director, 200);
        decide("approve", requestId, director, 409);

        assertThat(jdbc.queryForObject("select count(*) from credit_note where invoice_id = ?", Long.class, scenario.invoiceId())).isEqualTo(1L);
        assertThat(outstandingOf(scenario.invoiceId())).isEqualTo("50000.00");
    }

    @Test
    void usersWithoutAccountingRolesCannotRequestCreditNotes() throws Exception {
        Scenario scenario = unpaidInvoice("80000.00");

        for (String role : new String[]{"PRECEPTOR", "ALUMNO", "DOCENTE"}) {
            requestCreditNote(scenario.invoiceId(), newStaff(role), creditNoteBody("BILLING_ERROR", "Error", 1, "1000.00"), 403);
        }
        assertThat(creditNoteCountOf(scenario.invoiceId())).isZero();
    }

    @Test
    @Tag("D-159")
    @Tag("D-134")
    void freeCreditFromAPaidInvoiceIsAllocatedToTheOldestOverdueInvoiceInTheSameTransaction() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Long studentId = newStudentId();
        Long accountId = newAccountId(studentId, Currency.ARS);
        Long overpaid = issueInvoiceOfStudent(studentId, accountId, "10000.00", LocalDate.of(2027, 3, 10));
        payAccount(accountId, "10000.00", LocalDate.of(2027, 3, 5));
        Long overdue = issueInvoiceOfStudent(studentId, accountId, "10000.00", LocalDate.of(2027, 4, 10));
        Staff director = newStaff("DIRECTOR");

        requestCreditNote(overpaid, director, creditNoteBody("BILLING_ERROR", "Cobro de más", 1, "10000.00"), 201);

        assertThat(jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, overpaid)).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, overdue)).isEqualTo("PAID");
        assertThat(amount("select unallocated_credit_amount from student_current_account where id = ?", accountId)).isEqualByComparingTo("0.00");
        assertThat(amount("select balance_amount from student_current_account where id = ?", accountId)).isEqualByComparingTo("0.00");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-153")
    void aCreditNoteOnAnInvoiceWithAPendingTransferIsRejected() throws Exception {
        Scenario scenario = unpaidInvoice("80000.00");
        jdbc.update("update invoice set payment_status = 'PAYMENT_UNDER_REVIEW' where id = ?", scenario.invoiceId());

        mockMvc.perform(post("/invoices/" + scenario.invoiceId() + "/credit-notes").header("Authorization", "Bearer " + newStaff("DIRECTOR").token())
                        .contentType(APPLICATION_JSON).content(creditNoteBody("BILLING_ERROR", "Error", 1, "1000.00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PENDING_BANK_TRANSFER_ON_INVOICE"));
    }

    @Test
    @Tag("D-238")
    void invalidRequestsAreRejectedWithTheirSpecificCodes() throws Exception {
        Scenario scenario = unpaidInvoice("80000.00");
        Staff director = newStaff("DIRECTOR");
        String url = "/invoices/" + scenario.invoiceId() + "/credit-notes";

        mockMvc.perform(post(url).header("Authorization", "Bearer " + director.token()).contentType(APPLICATION_JSON)
                .content(creditNoteBody("WITHDRAWAL", "Baja", 1, "1000.00"))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CREDIT_NOTE_REASON"));
        mockMvc.perform(post(url).header("Authorization", "Bearer " + director.token()).contentType(APPLICATION_JSON)
                .content(creditNoteBody("RETROACTIVE_SCHOLARSHIP", "Beca", 1, "1000.00"))).andExpect(status().isBadRequest());
        mockMvc.perform(post(url).header("Authorization", "Bearer " + director.token()).contentType(APPLICATION_JSON)
                .content(creditNoteBody("BILLING_ERROR", " ", 1, "1000.00"))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CREDIT_NOTE_JUSTIFICATION_REQUIRED"));
        mockMvc.perform(post(url).header("Authorization", "Bearer " + director.token()).contentType(APPLICATION_JSON)
                .content(creditNoteBody("BILLING_ERROR", "Error", 1, "80000.01"))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CREDIT_NOTE_EXCEEDS_CREDITABLE_AMOUNT"));
        mockMvc.perform(post("/invoices/987654321/credit-notes").header("Authorization", "Bearer " + director.token()).contentType(APPLICATION_JSON)
                .content(creditNoteBody("BILLING_ERROR", "Error", 1, "1.00"))).andExpect(status().isNotFound());
        assertThat(creditNoteCountOf(scenario.invoiceId())).isZero();
    }

    @Test
    @Tag("D-244")
    void aNationalIdCorrectedAfterIssuanceDoesNotBlockTheCreditNoteAndTheNoteKeepsTheInvoiceRecipient() throws Exception {
        Scenario scenario = unpaidInvoice("80000.00");
        String snapshotBefore = jdbc.queryForObject("select student_snapshot ->> 'documentNumber' from invoice where id = ?", String.class, scenario.invoiceId());
        jdbc.update("update person set dni = '99999999' where id = (select person_id from student where id = ?)", scenario.studentId());

        requestCreditNote(scenario.invoiceId(), newStaff("DIRECTOR"), creditNoteBody("BILLING_ERROR", "Error", 1, "1000.00"), 201);

        assertThat(jdbc.queryForObject("select recipient_snapshot ->> 'documentNumber' from credit_note where invoice_id = ?", String.class, scenario.invoiceId()))
                .isEqualTo(snapshotBefore).isNotEqualTo("99999999");
    }

    @Test
    @Tag("D-167")
    void aCreditNoteThatCoversTheWholeUnpaidDebtIsRejectedBecauseThatIsACancellation() throws Exception {
        Scenario scenario = unpaidInvoice("80000.00");
        Staff director = newStaff("DIRECTOR");
        String url = "/invoices/" + scenario.invoiceId() + "/credit-notes";

        mockMvc.perform(post(url).header("Authorization", "Bearer " + director.token()).contentType(APPLICATION_JSON)
                        .content(creditNoteBody("BILLING_ERROR", "Error", 1, "80000.00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.violations[0].code").value("CREDIT_NOTE_FULL_DEBT_REQUIRES_CANCELLATION"))
                .andExpect(jsonPath("$.violations[0].message").value("Para anular toda la deuda de la factura se usa la cancelación, no una nota de crédito."));
        mockMvc.perform(post(url).header("Authorization", "Bearer " + director.token()).contentType(APPLICATION_JSON)
                .content(creditNoteBody("LATE_FEE_ANNULMENT", "Mora mal cargada", 1, "80000.00"))).andExpect(status().isConflict());

        assertThat(creditNoteCountOf(scenario.invoiceId())).isZero();
        assertThat(jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, scenario.invoiceId())).isEqualTo("PENDING");
        assertThat(outstandingOf(scenario.invoiceId())).isEqualTo("80000.00");
    }

    @Test
    @Tag("D-165")
    void anAdministrativeRequestWithAThousandCharactersOfJustificationIsAccepted() throws Exception {
        Scenario scenario = unpaidInvoice("80000.00");

        MvcResult pending = requestCreditNote(scenario.invoiceId(), newStaff("ADMINISTRATIVO"), creditNoteBody("BILLING_ERROR", "x".repeat(1000), 1, "1000.00"), 202);

        assertThat(jdbc.queryForObject("select length(reason) from authorization_request where id = ?", Integer.class, pendingRequestId(pending))).isBetween(1000, 1200);
    }

    @Test
    @Tag("D-134")
    @Tag("D-165")
    void anOffsetCreditNoteAllocatesTheCreditThatWasWaitingForTheInvoice() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Long studentId = newStudentId();
        Long accountId = newAccountId(studentId, Currency.ARS);
        Long invoiceId = issueInvoiceOfStudent(studentId, accountId, "80000.00", LocalDate.of(2027, 3, 10));
        payAccount(accountId, "50000.00", LocalDate.of(2027, 3, 5));
        assertThat(amount("select unallocated_credit_amount from student_current_account where id = ?", accountId)).isEqualByComparingTo("50000.00");

        requestCreditNote(invoiceId, newStaff("DIRECTOR"), creditNoteBody("BILLING_ERROR", "Error", 1, "30000.00"), 201);

        assertThat(jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, invoiceId)).isEqualTo("PAID");
        assertThat(amount("select unallocated_credit_amount from student_current_account where id = ?", accountId)).isEqualByComparingTo("0.00");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-165")
    void aFreeCreditNoteLargerThanWhatWasPaidIsRejectedWithTheRefundCode() throws Exception {
        Long studentId = newStudentId();
        Long accountId = newAccountId(studentId, Currency.ARS);
        Long invoiceId = issueInvoiceOfStudent(studentId, accountId, "80000.00", LocalDate.of(2027, 3, 10));
        Staff director = newStaff("DIRECTOR");
        requestCreditNote(invoiceId, director, creditNoteBody("BILLING_ERROR", "Error", 1, "40000.00"), 201);
        payAccount(accountId, "40000.00", LocalDate.of(2027, 3, 5));
        assertThat(jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, invoiceId)).isEqualTo("PAID");

        mockMvc.perform(post("/invoices/" + invoiceId + "/credit-notes").header("Authorization", "Bearer " + director.token()).contentType(APPLICATION_JSON)
                        .content(creditNoteBody("BILLING_ERROR", "Error", 1, "80000.00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CREDIT_NOTE_REFUND_EXCEEDS_PAID_AMOUNT"));
        assertThat(creditNoteCountOf(invoiceId)).isEqualTo(1L);
    }

    @Test
    @Tag("D-165")
    void aLineInAnotherCurrencyThanTheInvoiceIsABadRequest() throws Exception {
        Scenario scenario = unpaidInvoice("80000.00");

        mockMvc.perform(post("/invoices/" + scenario.invoiceId() + "/credit-notes").header("Authorization", "Bearer " + newStaff("DIRECTOR").token()).contentType(APPLICATION_JSON)
                        .content(creditNoteBody("BILLING_ERROR", "Error", 1, "1000.00").replace("\"ARS\"", "\"USD\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CREDIT_NOTE_LINES_INVALID"));
        assertThat(creditNoteCountOf(scenario.invoiceId())).isZero();
    }

    @Test
    @Tag("D-253")
    void creditNoteRequestsHaveNoExpiryDateAndTheExpirerLeavesThemPending() throws Exception {
        Scenario scenario = unpaidInvoice("80000.00");
        long requestId = pendingRequestId(requestCreditNote(scenario.invoiceId(), newStaff("ADMINISTRATIVO"), creditNoteBody("BILLING_ERROR", "Error", 1, "1000.00"), 202));

        assertThat(jdbc.queryForObject("select expires_on from authorization_request where id = ?", java.sql.Date.class, requestId)).isNull();
        MutableClock.INSTANCE.advance(Duration.ofDays(400));
        authorizationRequestExpirer.expireDueRequests();

        assertThat(jdbc.queryForObject("select status from authorization_request where id = ?", String.class, requestId)).isEqualTo("PENDING");
    }

    @Test
    @Tag("D-091")
    void theManualCreditNoteEndpointOfTheStudentIsGone() throws Exception {
        Scenario scenario = unpaidInvoice("80000.00");

        int statusCode = mockMvc.perform(post("/students/" + scenario.studentId() + "/credit-notes").header("Authorization", "Bearer " + newStaff("DIRECTOR").token())
                .contentType(APPLICATION_JSON).content("{}")).andReturn().getResponse().getStatus();

        assertThat(statusCode).isIn(404, 405);
    }

    @Test
    void issuedCreditNotesCannotBeChangedOrDeleted() throws Exception {
        Scenario scenario = unpaidInvoice("80000.00");
        requestCreditNote(scenario.invoiceId(), newStaff("DIRECTOR"), creditNoteBody("BILLING_ERROR", "Error", 1, "1000.00"), 201);

        assertThatThrownBy(() -> jdbc.update("update credit_note set justification = 'otra' where invoice_id = ?", scenario.invoiceId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("delete from credit_note_line where credit_note_id in (select id from credit_note where invoice_id = ?)", scenario.invoiceId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("delete from credit_note where invoice_id = ?", scenario.invoiceId())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theDatabaseRejectsMoreCreditThanTheNetAmountOfALine() throws Exception {
        Scenario scenario = unpaidInvoice("80000.00");

        assertThatThrownBy(() -> jdbc.update("update invoice_line set credited_amount = 40000.00, free_credited_amount = 40000.01 where invoice_id = ?", scenario.invoiceId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private long creditNoteCountOf(Long invoiceId) {
        return jdbc.queryForObject("select count(*) from credit_note where invoice_id = ?", Long.class, invoiceId);
    }
}
