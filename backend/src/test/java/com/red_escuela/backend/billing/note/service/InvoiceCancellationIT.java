package com.red_escuela.backend.billing.note.service;

import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDraftHeader;
import com.red_escuela.backend.billing.invoice.domain.InvoiceFactory;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.invoice.domain.InvoiceLineDraft;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.invoice.domain.StudentSnapshot;
import com.red_escuela.backend.billing.invoice.domain.event.InvoiceCancelledEvent;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.red_escuela.backend.billing.account.domain.AccountTestData.ars;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.ACTOR;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.discount;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RecordApplicationEvents
@Tag("D-127")
@Tag("D-216")
class InvoiceCancellationIT extends AbstractInvoiceCancellationIT {

    @Autowired
    private ApplicationEvents events;
    @Autowired
    private com.red_escuela.backend.billing.authorization.service.AuthorizationRequestExpirer authorizationRequestExpirer;

    private InvoiceLineDraft annualLine(int number, long tag, Long priceRevisionId) {
        return new InvoiceLineDraft(com.red_escuela.backend.billing.invoice.domain.FeeType.MONTHLY_INSTALLMENT, "Cuota " + number,
                java.time.YearMonth.of(2027, 2 + number), null, null, null, null, priceRevisionId, "ANN:" + tag + ":" + number, ars("1000.00"));
    }

    private Long issueDiscountedAnnualInvoice(Long studentId, Long accountId) {
        return inTransaction(() -> {
            Person person = studentPerson(studentId);
            StudentSnapshot snapshot = new StudentSnapshot(person.getName() + " " + person.getLastname(), person.getDni(), "Calle 1");
            InvoiceDraftHeader header = new InvoiceDraftHeader(accountId, Currency.ARS, InvoiceDocumentType.INVOICE, InvoiceType.ANNUAL_UPFRONT,
                    InvoiceIssuingReason.ANNUAL_UPFRONT_PAYMENT, null);
            long tag = System.nanoTime();
            Long programId = jdbc.queryForObject("insert into program (name, status, total_years, currency) values (?, 'ACTIVE', 1, 'ARS') returning id", Long.class, "Fixture " + tag);
            Long revisionId = com.red_escuela.backend.billing.support.PriceFixtures.persistPriceVersion(programId, null, com.red_escuela.backend.billing.invoice.domain.FeeType.MONTHLY_INSTALLMENT, "Test", com.red_escuela.backend.shared.domain.Money.of(new java.math.BigDecimal("1000.00"), com.red_escuela.backend.shared.domain.Currency.ARS), java.time.LocalDate.of(2027, 1, 1), person.getId());
            List<InvoiceLineDraft> lines = List.of(annualLine(1, tag, revisionId), annualLine(2, tag, revisionId), annualLine(3, tag, revisionId));
            Invoice draft = InvoiceFactory.createAnnualUpfrontInvoiceDraft(header, lines,
                    Map.of(1, discount("1000.00", "333.33"), 2, discount("1000.00", "333.33"), 3, discount("1000.00", "333.34")), ACTOR);
            draft.issueInvoice(numberGenerator.nextInvoiceNumber(InvoiceDocumentType.INVOICE), DUE_DATE.minusDays(9), DUE_DATE, snapshot, ACTOR);
            Invoice saved = invoiceRepository.save(draft);
            service.recordInvoiceIssuedDebit(service.lockStudentCurrentAccount(accountId), saved, DUE_DATE.minusDays(9));
            return saved.getId();
        });
    }

    @Test
    @Tag("B2-05")
    @Tag("D-036")
    void directorCancelsOverdueInstallmentByBillingError() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Scenario scenario = unpaidInvoice("80000.00");
        markOverdue(scenario.invoiceId());
        MutableClock.INSTANCE.setDate(DUE_DATE.plusDays(9));
        Map<String, Object> before = jdbc.queryForMap("select total, lines_total, issue_date, due_date from invoice where id = ?", scenario.invoiceId());
        BigDecimal balanceBefore = amount("select balance_amount from student_current_account where id = ?", scenario.accountId());

        requestCancellation(scenario.invoiceId(), newStaff("DIRECTOR"), cancellationBody("BILLING_ERROR", "Cuota mal emitida"), 201);

        assertThat(jdbc.queryForMap("select total_amount, offset_amount, free_credit_amount, reason from credit_note where invoice_id = ?", scenario.invoiceId()))
                .containsEntry("reason", "BILLING_ERROR")
                .satisfies(row -> assertThat(new BigDecimal(row.get("total_amount").toString())).isEqualByComparingTo("80000.00"))
                .satisfies(row -> assertThat(new BigDecimal(row.get("offset_amount").toString())).isEqualByComparingTo("80000.00"))
                .satisfies(row -> assertThat(new BigDecimal(row.get("free_credit_amount").toString())).isEqualByComparingTo("0.00"));
        assertThat(paymentStatusOf(scenario.invoiceId())).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForMap("select from_status, to_status, event from invoice_status_change where invoice_id = ? and status_kind = 'PAYMENT' order by id desc limit 1",
                scenario.invoiceId())).containsEntry("from_status", "OVERDUE").containsEntry("to_status", "CANCELLED")
                .containsEntry("event", "CANCELLATION_CREDIT_NOTE_APPLIED");
        assertThat(jdbc.queryForMap("select total, lines_total, issue_date, due_date from invoice where id = ?", scenario.invoiceId())).isEqualTo(before);
        assertThat(jdbc.queryForObject("select count(*) from invoice_line where invoice_id = ? and amount = 80000.00 and credited_amount = 80000.00", Long.class,
                scenario.invoiceId())).isEqualTo(1L);
        assertThat(amount("select balance_amount from student_current_account where id = ?", scenario.accountId())).isEqualByComparingTo("0.00");
        assertThat(balanceBefore).isEqualByComparingTo("80000.00");
        assertThat(events.stream(InvoiceCancelledEvent.class).filter(event -> event.invoiceId().equals(scenario.invoiceId())).count()).isEqualTo(1L);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("B2-06")
    @Tag("D-165")
    void cancellationOfPartiallyCreditedInvoiceCoversOnlyTheOutstanding() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Scenario scenario = unpaidInvoice("80000.00");
        Staff director = newStaff("DIRECTOR");
        requestCreditNote(scenario.invoiceId(), director, creditNoteBody("BILLING_ERROR", "Error", 1, "30000.00"), 201);

        requestCancellation(scenario.invoiceId(), director, cancellationBody("BILLING_ERROR", "Resto mal emitido"), 201);

        assertThat(jdbc.queryForObject("select total_amount from credit_note where invoice_id = ? and reason = 'BILLING_ERROR' order by id desc limit 1", java.math.BigDecimal.class,
                scenario.invoiceId())).isEqualByComparingTo("50000.00");
        assertThat(paymentStatusOf(scenario.invoiceId())).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("select credited_amount from invoice_line where invoice_id = ?", java.math.BigDecimal.class, scenario.invoiceId())).isEqualByComparingTo("80000.00");
        assertThat(amount("select balance_amount from student_current_account where id = ?", scenario.accountId())).isEqualByComparingTo("0.00");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("B2-07")
    @Tag("D-036")
    void cancellationOfDiscountedInvoiceUsesStoredLineNets() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Long studentId = newStudentId();
        Long accountId = newAccountId(studentId, Currency.ARS);
        Long invoiceId = issueDiscountedAnnualInvoice(studentId, accountId);

        requestCancellation(invoiceId, newStaff("DIRECTOR"), cancellationBody("CANCELLATION", "Baja"), 201);

        assertThat(jdbc.queryForList("select l.amount from credit_note_line l join credit_note n on n.id = l.credit_note_id where n.invoice_id = ? order by l.invoice_line_number",
                java.math.BigDecimal.class, invoiceId)).extracting(java.math.BigDecimal::toPlainString).containsExactly("666.67", "666.67", "666.66");
        assertThat(jdbc.queryForObject("select total_amount from credit_note where invoice_id = ?", java.math.BigDecimal.class, invoiceId)).isEqualByComparingTo("2000.00");
        assertThat(paymentStatusOf(invoiceId)).isEqualTo("CANCELLED");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("B2-40")
    @Tag("D-036")
    void partialCreditNoteOnDiscountedInvoiceTakesTheInvoiceLockWithoutFailing() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Long studentId = newStudentId();
        Long accountId = newAccountId(studentId, Currency.ARS);
        Long invoiceId = issueDiscountedAnnualInvoice(studentId, accountId);

        requestCreditNote(invoiceId, newStaff("DIRECTOR"), creditNoteBody("BILLING_ERROR", "Error", 1, "100.00"), 201);

        assertThat(creditNoteCountOf(invoiceId)).isEqualTo(1L);
        assertThat(outstandingOf(invoiceId)).isEqualTo("1900.00");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("B2-08")
    @Tag("D-165")
    void administrativeRequestWaitsForDirector() throws Exception {
        Scenario scenario = unpaidInvoice("80000.00");
        long entriesBefore = ledgerEntryCount(scenario.accountId());
        long notesBefore = creditNoteCount();

        MvcResult pending = requestCancellation(scenario.invoiceId(), newStaff("ADMINISTRATIVO"), cancellationBody("BILLING_ERROR", "Error"), 202);

        long requestId = pendingRequestId(pending);
        assertThat(jdbc.queryForMap("select status, operation_type, required_role from authorization_request where id = ?", requestId))
                .containsEntry("status", "PENDING").containsEntry("operation_type", "CANCEL_INVOICE").containsEntry("required_role", "DIRECTOR");
        assertThat(creditNoteCount()).isEqualTo(notesBefore);
        assertThat(ledgerEntryCount(scenario.accountId())).isEqualTo(entriesBefore);
        assertThat(paymentStatusOf(scenario.invoiceId())).isEqualTo("PENDING");
        assertThat(outstandingOf(scenario.invoiceId())).isEqualTo("80000.00");
    }

    @Test
    @Tag("B2-09")
    void approvalCancelsAndRejectionOrWithdrawalLeaveNoTrace() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Scenario approved = unpaidInvoice("80000.00");
        Scenario rejected = unpaidInvoice("80000.00");
        Scenario withdrawn = unpaidInvoice("80000.00");
        Staff administrative = newStaff("ADMINISTRATIVO");
        Staff director = newStaff("DIRECTOR");
        long approveId = pendingRequestId(requestCancellation(approved.invoiceId(), administrative, cancellationBody("BILLING_ERROR", "Error"), 202));
        long rejectId = pendingRequestId(requestCancellation(rejected.invoiceId(), administrative, cancellationBody("BILLING_ERROR", "Error"), 202));
        long withdrawId = pendingRequestId(requestCancellation(withdrawn.invoiceId(), administrative, cancellationBody("BILLING_ERROR", "Error"), 202));

        decide("approve", approveId, director, 200);
        decide("reject", rejectId, director, 200);
        decide("withdraw", withdrawId, administrative, 200);

        assertThat(paymentStatusOf(approved.invoiceId())).isEqualTo("CANCELLED");
        assertThat(creditNoteCountOf(approved.invoiceId())).isEqualTo(1L);
        assertThat(jdbc.queryForMap("select authorization_request_id, issued_by_person_id from credit_note where invoice_id = ?", approved.invoiceId()))
                .containsEntry("authorization_request_id", approveId).containsEntry("issued_by_person_id", administrative.personId());
        for (Scenario untouched : List.of(rejected, withdrawn)) {
            assertThat(paymentStatusOf(untouched.invoiceId())).isEqualTo("PENDING");
            assertThat(creditNoteCountOf(untouched.invoiceId())).isZero();
            assertThat(outstandingOf(untouched.invoiceId())).isEqualTo("80000.00");
        }
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("B2-10")
    @Tag("D-091")
    void approvalRevalidatesWhenInvoiceWasPaidMeanwhile() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Scenario scenario = unpaidInvoice("80000.00");
        Staff administrative = newStaff("ADMINISTRATIVO");
        long requestId = pendingRequestId(requestCancellation(scenario.invoiceId(), administrative, cancellationBody("BILLING_ERROR", "Error"), 202));
        BillingTestFixtures.payInvoiceInFull(invoiceRepository.findById(scenario.invoiceId()).orElseThrow());

        MvcResult result = mockMvc.perform(post("/authorization-requests/" + requestId + "/approve").header("Authorization", "Bearer " + newStaff("DIRECTOR").token())
                .contentType(APPLICATION_JSON).content("{\"decisionReason\":\"ok\"}")).andExpect(status().isConflict()).andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("ILLEGAL_INVOICE_PAYMENT_STATUS_TRANSITION");
        assertThat(jdbc.queryForMap("select status, resume_outcome from authorization_request where id = ?", requestId))
                .containsEntry("status", "APPROVED").containsEntry("resume_outcome", "REJECTED_ON_RESUME");
        assertThat(creditNoteCountOf(scenario.invoiceId())).isZero();
        assertThat(paymentStatusOf(scenario.invoiceId())).isEqualTo("PAID");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("B2-11")
    @Tag("D-153")
    void paidAndUnderReviewInvoicesAreRejected() throws Exception {
        Staff director = newStaff("DIRECTOR");
        Scenario paid = unpaidInvoice("80000.00");
        payAccount(paid.accountId(), "80000.00", LocalDate.of(2027, 3, 11));
        Scenario underReview = unpaidInvoice("80000.00");
        jdbc.update("update invoice set payment_status = 'PAYMENT_UNDER_REVIEW' where id = ?", underReview.invoiceId());

        MvcResult paidResult = requestCancellation(paid.invoiceId(), director, cancellationBody("BILLING_ERROR", "Error"), 409);
        MvcResult reviewResult = requestCancellation(underReview.invoiceId(), director, cancellationBody("BILLING_ERROR", "Error"), 409);

        assertThat(paidResult.getResponse().getContentAsString()).contains("ILLEGAL_INVOICE_PAYMENT_STATUS_TRANSITION");
        assertThat(reviewResult.getResponse().getContentAsString()).contains("PENDING_BANK_TRANSFER_ON_INVOICE");
        assertThat(paymentStatusOf(paid.invoiceId())).isEqualTo("PAID");
        assertThat(paymentStatusOf(underReview.invoiceId())).isEqualTo("PAYMENT_UNDER_REVIEW");
        assertThat(creditNoteCountOf(paid.invoiceId())).isZero();
        assertThat(creditNoteCountOf(underReview.invoiceId())).isZero();
    }

    @Test
    @Tag("D-127")
    @Tag("D-375")
    void lateFeeCorrectionReasonIsRejected() throws Exception {
        Scenario scenario = unpaidInvoice("80000.00");

        MvcResult result = requestCancellation(scenario.invoiceId(), newStaff("DIRECTOR"), cancellationBody("LATE_FEE_CORRECTION", "Correccion"), 400);

        assertThat(result.getResponse().getContentAsString()).contains("INVALID_CREDIT_NOTE_REASON");
    }

    @Test
    @Tag("B2-12")
    @Tag("D-238")
    void withdrawalReasonIsRejected() throws Exception {
        Scenario scenario = unpaidInvoice("80000.00");

        MvcResult result = requestCancellation(scenario.invoiceId(), newStaff("DIRECTOR"), cancellationBody("WITHDRAWAL", "Baja"), 400);

        assertThat(result.getResponse().getContentAsString()).contains("INVALID_CREDIT_NOTE_REASON");
        assertThat(creditNoteCountOf(scenario.invoiceId())).isZero();
    }

    @Test
    @Tag("B2-13")
    @Tag("D-253")
    void cancellationRequestNeverExpiresAndIsRevalidatedOnApproval() throws Exception {
        Scenario scenario = unpaidInvoice("80000.00");
        Staff director = newStaff("DIRECTOR");
        long requestId = pendingRequestId(requestCancellation(scenario.invoiceId(), newStaff("ADMINISTRATIVO"), cancellationBody("CANCELLATION", "Baja"), 202));
        assertThat(jdbc.queryForObject("select expires_on from authorization_request where id = ?", java.sql.Date.class, requestId)).isNull();

        MutableClock.INSTANCE.advance(Duration.ofDays(90));
        authorizationRequestExpirer.expireDueRequests();

        assertThat(jdbc.queryForObject("select status from authorization_request where id = ?", String.class, requestId)).isEqualTo("PENDING");
        BillingTestFixtures.payInvoiceInFull(invoiceRepository.findById(scenario.invoiceId()).orElseThrow());
        decide("approve", requestId, director, 409);
        assertThat(jdbc.queryForObject("select resume_outcome from authorization_request where id = ?", String.class, requestId)).isEqualTo("REJECTED_ON_RESUME");
    }

    @Test
    @Tag("B2-18")
    @Tag("D-202")
    void partialBillingErrorCreditNoteKeepsTheKey() throws Exception {
        Scenario scenario = unpaidInvoice("90000.00");

        requestCreditNote(scenario.invoiceId(), newStaff("DIRECTOR"), creditNoteBody("BILLING_ERROR", "Monto de más", 1, "10000.00"), 201);

        assertThat(paymentStatusOf(scenario.invoiceId())).isEqualTo("PENDING");
        assertThat(outstandingOf(scenario.invoiceId())).isEqualTo("80000.00");
        assertThat(jdbc.queryForObject("select count(*) from invoice_line where invoice_id = ? and charge_key_released_at is null", Long.class, scenario.invoiceId())).isEqualTo(1L);
    }

    @Test
    @Tag("B2-19")
    @Tag("D-202")
    void lateFeeAnnulmentAndCancellationDoNotReleaseTheKey() throws Exception {
        Scenario annulled = unpaidInvoice("5000.00");
        Scenario cancelled = unpaidInvoice("80000.00");
        Staff director = newStaff("DIRECTOR");

        requestCancellation(annulled.invoiceId(), director, cancellationBody("LATE_FEE_ANNULMENT", "Recargo mal cargado"), 201);
        requestCancellation(cancelled.invoiceId(), director, cancellationBody("CANCELLATION", "Baja"), 201);

        for (Scenario scenario : List.of(annulled, cancelled)) {
            assertThat(paymentStatusOf(scenario.invoiceId())).isEqualTo("CANCELLED");
            assertThat(jdbc.queryForObject("select count(*) from invoice_line where invoice_id = ? and charge_key_released_at is null", Long.class, scenario.invoiceId())).isEqualTo(1L);
        }
    }

    @Test
    @Tag("B2-37")
    @Tag("D-202")
    void billingErrorReleasesTheKeyOfEveryLine() throws Exception {
        Long studentId = newStudentId();
        Long accountId = newAccountId(studentId, Currency.ARS);
        Long invoiceId = issueDiscountedAnnualInvoice(studentId, accountId);

        requestCancellation(invoiceId, newStaff("DIRECTOR"), cancellationBody("BILLING_ERROR", "Error"), 201);

        assertThat(jdbc.queryForObject("select count(*) from invoice_line where invoice_id = ? and charge_key_released_at is not null", Long.class, invoiceId)).isEqualTo(3L);
    }

    @Test
    @Tag("B2-23")
    @Tag("D-165")
    void theEndpointIgnoresAnySystemInitiatedField() throws Exception {
        Scenario scenario = unpaidInvoice("80000.00");

        MvcResult result = mockMvc.perform(post("/invoices/" + scenario.invoiceId() + "/cancellations")
                        .header("Authorization", "Bearer " + newStaff("ADMINISTRATIVO").token()).contentType(APPLICATION_JSON)
                        .content("{\"reason\":\"CANCELLATION\",\"justification\":\"Baja\",\"systemInitiated\":true,\"systemOrigin\":\"ADMISSION_EXPIRED\",\"requestedByDirector\":true}"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(202);
        long requestId = pendingRequestId(result);
        String payload = jdbc.queryForObject("select operation_payload from authorization_request where id = ?", String.class, requestId);
        assertThat((Object) com.jayway.jsonpath.JsonPath.read(payload, "$.systemOrigin")).isNull();
        assertThat((Boolean) com.jayway.jsonpath.JsonPath.read(payload, "$.requestedByDirector")).isFalse();
        assertThat(paymentStatusOf(scenario.invoiceId())).isEqualTo("PENDING");
        assertThat(creditNoteCountOf(scenario.invoiceId())).isZero();
    }

    @Test
    @Tag("B2-28")
    @Tag("D-165")
    void approvingTheSameCancellationTwiceIssuesASingleCreditNote() throws Exception {
        Scenario scenario = unpaidInvoice("80000.00");
        Staff director = newStaff("DIRECTOR");
        long requestId = pendingRequestId(requestCancellation(scenario.invoiceId(), newStaff("ADMINISTRATIVO"), cancellationBody("CANCELLATION", "Baja"), 202));

        decide("approve", requestId, director, 200);
        decide("approve", requestId, director, 409);

        assertThat(creditNoteCountOf(scenario.invoiceId())).isEqualTo(1L);
        assertThat(paymentStatusOf(scenario.invoiceId())).isEqualTo("CANCELLED");
    }

    @Test
    @Tag("B2-29")
    void cancellationLateInTheEveningUsesTheLocalBusinessDate() throws Exception {
        Scenario scenario = unpaidInvoice("80000.00");
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2027, 3, 12, 23, 30));

        requestCancellation(scenario.invoiceId(), newStaff("DIRECTOR"), cancellationBody("BILLING_ERROR", "Error"), 201);

        assertThat(jdbc.queryForMap("select issue_date::text as issue_date, fact_date::text as fact_date from credit_note where invoice_id = ?", scenario.invoiceId()))
                .containsEntry("issue_date", "2027-03-12").containsEntry("fact_date", "2027-03-12");
        assertThat(jdbc.queryForObject("select business_date::text from invoice_status_change where invoice_id = ? and status_kind = 'PAYMENT' order by id desc limit 1",
                String.class, scenario.invoiceId())).isEqualTo("2027-03-12");
    }

    @Test
    @Tag("B2-30")
    @Tag("D-110")
    void onlyAdministrativeAndDirectorRolesCanCancel() throws Exception {
        Scenario scenario = unpaidInvoice("80000.00");

        for (String role : new String[]{"PRECEPTOR", "DOCENTE", "ALUMNO"}) {
            requestCancellation(scenario.invoiceId(), newStaff(role), cancellationBody("CANCELLATION", "Baja"), 403);
        }
        requestCancellation(scenario.invoiceId(), newStaff("ADMINISTRATIVO"), cancellationBody("CANCELLATION", "Baja"), 202);
        requestCancellation(scenario.invoiceId(), newStaff("DIRECTOR"), cancellationBody("CANCELLATION", "Baja"), 201);

        assertThat(creditNoteCountOf(scenario.invoiceId())).isEqualTo(1L);
    }

    @Test
    @Tag("B2-31")
    void administrativeRequestApprovedByDirectorIsVisibleToTheStudent() throws Exception {
        Scenario scenario = unpaidInvoice("80000.00");
        Staff administrative = newStaff("ADMINISTRATIVO");
        Staff director = newStaff("DIRECTOR");
        Staff student = studentStaff(scenario.studentId());
        long requestId = pendingRequestId(requestCancellation(scenario.invoiceId(), administrative, cancellationBody("CANCELLATION", "Baja"), 202));

        decide("approve", requestId, director, 200);

        assertThat(paymentStatusOf(scenario.invoiceId())).isEqualTo("CANCELLED");
        mockMvc.perform(get("/me/credit-notes").header("Authorization", "Bearer " + student.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].invoiceId").value(scenario.invoiceId()))
                .andExpect(jsonPath("$[0].reason").value("CANCELLATION"));
    }

    @Test
    @Tag("B2-35")
    @Tag("D-127")
    void invoicePaidWithFreeCreditAndThenReversedCanStillBeCancelled() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Scenario scenario = unpaidInvoice("80000.00");
        Staff director = newStaff("DIRECTOR");
        payAccount(scenario.accountId(), "80000.00", LocalDate.of(2027, 3, 5));
        requestCreditNote(scenario.invoiceId(), director, creditNoteBody("BILLING_ERROR", "Cobro de más", 1, "10000.00"), 201);
        inTransaction(() -> {
            StudentCurrentAccount account = service.lockStudentCurrentAccount(scenario.accountId());
            return allocationService.reverseNewestAllocationsUntilCreditCovers(account, ars("80000.00"), LocalDate.of(2027, 3, 20), 0, "Contracargo", ACTOR);
        });
        assertThat(outstandingOf(scenario.invoiceId())).isEqualTo("80000.00");
        assertThat(jdbc.queryForObject("select free_credited_amount from invoice_line where invoice_id = ?", java.math.BigDecimal.class, scenario.invoiceId())).isEqualByComparingTo("10000.00");

        requestCancellation(scenario.invoiceId(), director, cancellationBody("BILLING_ERROR", "Factura mal emitida"), 201);

        assertThat(paymentStatusOf(scenario.invoiceId())).isEqualTo("CANCELLED");
        assertThat(outstandingOf(scenario.invoiceId())).isEqualTo("0.00");
        assertThat(jdbc.queryForObject("select total_amount from credit_note where invoice_id = ? and reason = 'BILLING_ERROR' and offset_amount > 0", java.math.BigDecimal.class,
                scenario.invoiceId())).isEqualByComparingTo("80000.00");
        assertThat(jdbc.queryForMap("select unallocated_credit_amount, balance_amount, balance_side from student_current_account where id = ?", scenario.accountId()))
                .satisfies(row -> assertThat(new java.math.BigDecimal(row.get("unallocated_credit_amount").toString())).isEqualByComparingTo("90000.00"))
                .satisfies(row -> assertThat(new java.math.BigDecimal(row.get("balance_amount").toString())).isEqualByComparingTo("90000.00"))
                .containsEntry("balance_side", "IN_FAVOR");
        assertThat(jdbc.queryForMap("select credited_amount, free_credited_amount from invoice_line where invoice_id = ?", scenario.invoiceId()))
                .satisfies(row -> assertThat(new java.math.BigDecimal(row.get("credited_amount").toString())).isEqualByComparingTo("80000.00"))
                .satisfies(row -> assertThat(new java.math.BigDecimal(row.get("free_credited_amount").toString())).isEqualByComparingTo("0.00"));
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("B2-36")
    @Tag("D-247")
    void rejectingOrWithdrawingARequestAllocatesTheAccountCreditAtThatMoment() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Scenario rejected = unpaidInvoice("80000.00");
        Scenario withdrawn = unpaidInvoice("80000.00");
        Staff administrative = newStaff("ADMINISTRATIVO");
        Staff director = newStaff("DIRECTOR");
        long rejectedRequest = pendingRequestId(requestCancellation(rejected.invoiceId(), administrative, cancellationBody("CANCELLATION", "Baja"), 202));
        long withdrawnRequest = pendingRequestId(requestCreditNote(withdrawn.invoiceId(), administrative, creditNoteBody("BILLING_ERROR", "Error", 1, "1000.00"), 202));
        for (Scenario scenario : List.of(rejected, withdrawn)) {
            inTransaction(() -> {
                service.recordPaymentReceivedCredit(service.lockStudentCurrentAccount(scenario.accountId()), BillingTestFixtures.seedApprovedPaymentRow(scenario.accountId()), ars("80000.00"), LocalDate.of(2027, 3, 11), "Pago");
                return null;
            });
            assertThat(paymentStatusOf(scenario.invoiceId())).isEqualTo("PENDING");
        }

        decide("reject", rejectedRequest, director, 200);
        decide("withdraw", withdrawnRequest, administrative, 200);

        assertThat(paymentStatusOf(rejected.invoiceId())).isEqualTo("PAID");
        assertThat(paymentStatusOf(withdrawn.invoiceId())).isEqualTo("PAID");
        assertThat(amount("select unallocated_credit_amount from student_current_account where id = ?", rejected.accountId())).isEqualByComparingTo("0.00");
        invariants.assertAllBillingInvariantsHold();
    }
}
