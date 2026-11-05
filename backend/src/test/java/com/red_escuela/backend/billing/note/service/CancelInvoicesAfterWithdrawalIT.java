package com.red_escuela.backend.billing.note.service;

import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDraftHeader;
import com.red_escuela.backend.billing.invoice.domain.InvoiceFactory;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.invoice.domain.InvoiceLineDraft;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.invoice.domain.StudentSnapshot;
import com.red_escuela.backend.billing.note.domain.CancelInvoicesAfterWithdrawalCommand;
import com.red_escuela.backend.billing.note.domain.WithdrawalCancellationItem;
import com.red_escuela.backend.billing.note.domain.WithdrawalLineAmount;
import com.red_escuela.backend.billing.note.exception.WithdrawalReductionRejectedException;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.billing.operation.BillingOperationOutcome;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.billing.support.PriceFixtures;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.domain.Currency;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.red_escuela.backend.billing.account.domain.AccountTestData.ars;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.ACTOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-204")
@Tag("D-247")
@Tag("D-248")
@Tag("D-253")
class CancelInvoicesAfterWithdrawalIT extends AbstractInvoiceCancellationIT {
    private static final LocalDate WITHDRAWAL = LocalDate.of(2027, 5, 15);

    @Autowired
    private BillingOperationPipeline pipeline;
    @Autowired
    private CancelInvoicesAfterWithdrawalOperation operation;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(Staff staff, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(staff.personId().toString(), null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private Long priceVersionForAnnual(Long studentId) {
        Long programId = jdbc.queryForObject("insert into program (name, status, total_years, currency) values (?, 'ACTIVE', 1, 'ARS') returning id", Long.class,
                "Anual " + System.nanoTime());
        return PriceFixtures.persistPriceVersion(programId, null, FeeType.MONTHLY_INSTALLMENT, "Anual", ars("80000.00").value(), LocalDate.of(2000, 1, 1),
                studentPerson(studentId).getId());
    }

    private Long issueAnnual(Long studentId, Long accountId) {
        Long priceVersionId = priceVersionForAnnual(studentId);
        return inTransaction(() -> {
            Person person = studentPerson(studentId);
            StudentSnapshot snapshot = new StudentSnapshot(person.getName() + " " + person.getLastname(), person.getDni(), "Calle 1");
            InvoiceDraftHeader header = new InvoiceDraftHeader(accountId, Currency.ARS, InvoiceDocumentType.INVOICE, InvoiceType.ANNUAL_UPFRONT,
                    InvoiceIssuingReason.MONTHLY_RUN, null);
            List<InvoiceLineDraft> lines = new ArrayList<>();
            for (int month = 3; month <= 12; month++) {
                lines.add(new InvoiceLineDraft(FeeType.MONTHLY_INSTALLMENT, "Cuota " + month, YearMonth.of(2027, month), null, null, null, null, priceVersionId,
                        "AN:" + System.nanoTime() + ":" + month, ars("80000.00")));
            }
            Invoice draft = InvoiceFactory.createAnnualUpfrontInvoiceDraft(header, lines, Map.of(), ACTOR);
            draft.issueInvoice(numberGenerator.nextInvoiceNumber(InvoiceDocumentType.INVOICE), DUE_DATE.minusDays(9), DUE_DATE, snapshot, ACTOR);
            Invoice saved = invoiceRepository.save(draft);
            service.recordInvoiceIssuedDebit(service.lockStudentCurrentAccount(accountId), saved, DUE_DATE.minusDays(9));
            return saved.getId();
        });
    }

    private long creditNoteCountOfAccount(Long accountId) {
        return jdbc.queryForObject("select count(*) from credit_note where student_current_account_id = ?", Long.class, accountId);
    }

    private record Setup(Long studentId, Long accountId, Long currentMonth, Long june, Long july, Long annual) {
    }

    private Setup setup(boolean withAnnual) {
        Scenario scenario = unpaidInvoice("80000.00");
        Long june = issueInvoiceOfStudent(scenario.studentId(), scenario.accountId(), "80000.00", DUE_DATE.plusDays(30));
        Long july = issueInvoiceOfStudent(scenario.studentId(), scenario.accountId(), "80000.00", DUE_DATE.plusDays(60));
        Long annual = withAnnual ? issueAnnual(scenario.studentId(), scenario.accountId()) : null;
        return new Setup(scenario.studentId(), scenario.accountId(), scenario.invoiceId(), june, july, annual);
    }

    private List<WithdrawalLineAmount> laterLines() {
        return List.of(4, 5, 6, 7, 8, 9, 10).stream().map(number -> new WithdrawalLineAmount(number, ars("80000.00").value())).toList();
    }

    private CancelInvoicesAfterWithdrawalCommand commandFor(Setup setup, Staff requester, boolean director) {
        List<WithdrawalCancellationItem> items = new ArrayList<>(List.of(WithdrawalCancellationItem.cancelling(setup.june()), WithdrawalCancellationItem.cancelling(setup.july())));
        if (setup.annual() != null) {
            items.add(WithdrawalCancellationItem.reducing(setup.annual(), laterLines()));
        }
        return new CancelInvoicesAfterWithdrawalCommand(items, WITHDRAWAL, requester.personId(), director, null);
    }

    private long requestWithAdministrative(Setup setup) {
        Staff administrative = newStaff("ADMINISTRATIVO");
        authenticateAs(administrative, "ADMINISTRATIVO");
        BillingOperationOutcome<?> outcome = inTransaction(() -> pipeline.runOperation(operation, commandFor(setup, administrative, false)));
        SecurityContextHolder.clearContext();
        assertThat(outcome).isInstanceOf(BillingOperationOutcome.AwaitingAuthorization.class);
        return ((BillingOperationOutcome.AwaitingAuthorization<?>) outcome).authorizationRequestId();
    }

    @Test
    @Tag("C-09")
    @Tag("C-13")
    void anAdministrativeWithdrawalOpensASingleRequestForAllTheLaterInvoicesAndTouchesNothing() {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Setup setup = setup(true);

        long requestId = requestWithAdministrative(setup);

        assertThat(jdbc.queryForObject("select count(*) from authorization_request where id = ? and status = 'PENDING' and operation_type = 'CANCEL_INVOICES_AFTER_WITHDRAWAL'",
                Long.class, requestId)).isEqualTo(1L);
        assertThat(jdbc.queryForList("select invoice_id from authorization_request_invoice where authorization_request_id = ?", Long.class, requestId))
                .containsExactlyInAnyOrder(setup.june(), setup.july(), setup.annual());
        assertThat(paymentStatusOf(setup.currentMonth())).isEqualTo("PENDING");
        assertThat(paymentStatusOf(setup.june())).isEqualTo("PENDING");
        assertThat(outstandingOf(setup.annual())).isEqualTo("800000.00");
        assertThat(creditNoteCountOfAccount(setup.accountId())).isZero();
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("C-10")
    @Tag("C-13")
    void approvingTheRequestCancelsTheLaterInvoicesReducesTheAnnualOneAndKeepsTheWithdrawalMonthAsDebt() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Setup setup = setup(true);
        long requestId = requestWithAdministrative(setup);

        decide("approve", requestId, newStaff("DIRECTOR"), 200);

        assertThat(paymentStatusOf(setup.june())).isEqualTo("CANCELLED");
        assertThat(paymentStatusOf(setup.july())).isEqualTo("CANCELLED");
        assertThat(paymentStatusOf(setup.currentMonth())).isEqualTo("PENDING");
        assertThat(paymentStatusOf(setup.annual())).isEqualTo("PENDING");
        assertThat(outstandingOf(setup.annual())).isEqualTo("240000.00");
        assertThat(jdbc.queryForObject("select count(*) from credit_note where authorization_request_id = ? and reason = 'CANCELLATION'", Long.class, requestId)).isEqualTo(3L);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("C-10")
    void rejectingTheRequestLeavesEveryInvoiceUnpaid() throws Exception {
        Setup setup = setup(true);
        long requestId = requestWithAdministrative(setup);

        decide("reject", requestId, newStaff("DIRECTOR"), 200);

        assertThat(paymentStatusOf(setup.june())).isEqualTo("PENDING");
        assertThat(paymentStatusOf(setup.july())).isEqualTo("PENDING");
        assertThat(outstandingOf(setup.annual())).isEqualTo("800000.00");
        assertThat(creditNoteCountOfAccount(setup.accountId())).isZero();
    }

    @Test
    @Tag("C-11")
    void aWithdrawalRegisteredByTheDirectorCancelsDirectlyWithoutARequest() {
        Setup setup = setup(true);
        Staff director = newStaff("DIRECTOR");
        authenticateAs(director, "DIRECTOR");
        long requestsBefore = jdbc.queryForObject("select count(*) from authorization_request", Long.class);

        BillingOperationOutcome<?> outcome = inTransaction(() -> pipeline.runOperation(operation, commandFor(setup, director, true)));

        assertThat(outcome).isInstanceOf(BillingOperationOutcome.Processed.class);
        assertThat(jdbc.queryForObject("select count(*) from authorization_request", Long.class)).isEqualTo(requestsBefore);
        assertThat(paymentStatusOf(setup.june())).isEqualTo("CANCELLED");
        assertThat(paymentStatusOf(setup.currentMonth())).isEqualTo("PENDING");
        assertThat(outstandingOf(setup.annual())).isEqualTo("240000.00");
    }

    @Test
    @Tag("C-12")
    void aPaymentSkipsTheInvoicesWithAPendingRequestAndReachesThemWhenTheDirectorRejects() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Setup setup = setup(false);
        long requestId = requestWithAdministrative(setup);

        payAccount(setup.accountId(), "240000.00", LocalDate.of(2027, 3, 11));

        assertThat(paymentStatusOf(setup.currentMonth())).isEqualTo("PAID");
        assertThat(paymentStatusOf(setup.june())).isEqualTo("PENDING");
        assertThat(paymentStatusOf(setup.july())).isEqualTo("PENDING");

        decide("reject", requestId, newStaff("DIRECTOR"), 200);

        assertThat(paymentStatusOf(setup.june())).isEqualTo("PAID");
        assertThat(paymentStatusOf(setup.july())).isEqualTo("PAID");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("B2-38")
    void ifOneInvoiceChangedBeforeTheApprovalTheWholeRequestIsRejectedAndNothingIsCancelled() throws Exception {
        Setup setup = setup(false);
        long requestId = requestWithAdministrative(setup);
        requestCancellation(setup.july(), newStaff("DIRECTOR"), cancellationBody("CANCELLATION", "Baja directa"), 201);

        decide("approve", requestId, newStaff("DIRECTOR"), 409);

        assertThat(jdbc.queryForMap("select status, resume_outcome from authorization_request where id = ?", requestId))
                .containsEntry("status", "APPROVED").containsEntry("resume_outcome", "REJECTED_ON_RESUME");
        assertThat(paymentStatusOf(setup.june())).isEqualTo("PENDING");
        assertThat(paymentStatusOf(setup.july())).isEqualTo("CANCELLED");
        assertThat(creditNoteCountOf(setup.june())).isZero();
    }

    @Test
    @Tag("B3-01")
    void approvingWithCreditOnTheAccountCancelsEveryInvoiceAndKeepsTheCredit() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Setup setup = setup(false);
        long requestId = requestWithAdministrative(setup);
        payAccount(setup.accountId(), "400000.00", LocalDate.of(2027, 3, 11));

        decide("approve", requestId, newStaff("DIRECTOR"), 200);

        assertThat(paymentStatusOf(setup.june())).isEqualTo("CANCELLED");
        assertThat(paymentStatusOf(setup.july())).isEqualTo("CANCELLED");
        assertThat(paymentStatusOf(setup.currentMonth())).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("select count(*) from credit_note where authorization_request_id = ?", Long.class, requestId)).isEqualTo(2L);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("B2-40")
    @Tag("D-236")
    void aPaidAnnualInvoiceIsNeverReducedNorRefunded() {
        Setup setup = setup(true);
        BillingTestFixtures.payInvoiceInFull(invoiceRepository.findById(setup.annual()).orElseThrow());
        Staff director = newStaff("DIRECTOR");
        authenticateAs(director, "DIRECTOR");

        assertThatThrownBy(() -> inTransaction(() -> pipeline.runOperation(operation, new CancelInvoicesAfterWithdrawalCommand(
                List.of(WithdrawalCancellationItem.reducing(setup.annual(), laterLines())), WITHDRAWAL, director.personId(), true, null))))
                .isInstanceOfSatisfying(WithdrawalReductionRejectedException.class,
                        rejection -> assertThat(rejection.violations().get(0).code()).isEqualTo("WITHDRAWAL_REDUCTION_INVOICE_NOT_REDUCIBLE"));

        assertThat(creditNoteCountOf(setup.annual())).isZero();
        assertThat(paymentStatusOf(setup.annual())).isEqualTo("PAID");
    }

    @Test
    @Tag("B2-41")
    @Tag("D-253")
    void ifAnAnnualLineChangedBeforeTheApprovalTheWholeRequestIsRejected() throws Exception {
        Setup setup = setup(true);
        long requestId = requestWithAdministrative(setup);
        Staff director = newStaff("DIRECTOR");
        requestCreditNote(setup.annual(), director, creditNoteBody("BILLING_ERROR", "Ajuste", 4, "10000.00"), 201);

        String body = approveExpectingConflict(requestId, director);

        assertThat(body).contains("WITHDRAWAL_REDUCTION_INVOICE_CHANGED");
        assertThat(jdbc.queryForObject("select resume_outcome from authorization_request where id = ?", String.class, requestId)).isEqualTo("REJECTED_ON_RESUME");
        assertThat(jdbc.queryForObject("select count(*) from credit_note where authorization_request_id = ?", Long.class, requestId)).isZero();
        assertThat(paymentStatusOf(setup.june())).isEqualTo("PENDING");
    }

    @Test
    @Tag("B2-42")
    @Tag("D-311")
    void ifTheAnnualInvoiceWentUnderReviewBeforeTheApprovalTheWholeRequestIsRejected() throws Exception {
        Setup setup = setup(true);
        long requestId = requestWithAdministrative(setup);
        jdbc.update("update invoice set payment_status = 'PAYMENT_UNDER_REVIEW' where id = ?", setup.annual());

        String body = approveExpectingConflict(requestId, newStaff("DIRECTOR"));

        assertThat(body).contains("WITHDRAWAL_REDUCTION_INVOICE_NOT_REDUCIBLE");
        assertThat(jdbc.queryForObject("select count(*) from credit_note where authorization_request_id = ?", Long.class, requestId)).isZero();
        assertThat(paymentStatusOf(setup.june())).isEqualTo("PENDING");
    }

    private String approveExpectingConflict(long requestId, Staff director) throws Exception {
        return mockMvc.perform(post("/authorization-requests/" + requestId + "/approve").header("Authorization", "Bearer " + director.token())
                .contentType(APPLICATION_JSON).content("{\"decisionReason\":\"ok\"}")).andExpect(status().isConflict()).andReturn().getResponse().getContentAsString();
    }
}
