package com.red_escuela.backend.billing.note.service;

import com.red_escuela.backend.billing.account.service.CurrentAccountAllocationService;
import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static com.red_escuela.backend.billing.account.domain.AccountTestData.ars;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-247")
class PendingCreditNoteRequestAllocationIT extends AbstractInvoiceCancellationIT {

    @Autowired
    private CurrentAccountAllocationService allocationService;

    private void allocateAccountCredit(Long accountId) {
        inTransaction(() -> {
            allocationService.allocateAccountCreditToOldestInvoices(service.lockStudentCurrentAccount(accountId), LocalDate.of(2027, 3, 11), StatusChangeActor.ofProcess("TEST"));
            return null;
        });
    }

    private void receivePayment(Long accountId) {
        inTransaction(() -> {
            service.recordPaymentReceivedCredit(service.lockStudentCurrentAccount(accountId), BillingTestFixtures.seedApprovedPaymentRow(accountId, ars("80000.00")),
                    ars("80000.00"), LocalDate.of(2027, 3, 11), "Pago");
            return null;
        });
    }

    @Test
    @Tag("B2-36")
    void anInvoiceWithAPendingCreditNoteRequestIsSkippedByTheAutomaticAllocationUntilTheDirectorDecides() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Scenario scenario = unpaidInvoice("80000.00");
        Staff administrative = newStaff("ADMINISTRATIVO");
        Staff director = newStaff("DIRECTOR");
        long requestId = pendingRequestId(requestCreditNote(scenario.invoiceId(), administrative, creditNoteBody("BILLING_ERROR", "Error", 1, "1000.00"), 202));

        assertThat(jdbc.queryForList("select invoice_id from authorization_request_invoice where authorization_request_id = ?", Long.class, requestId))
                .containsExactly(scenario.invoiceId());
        receivePayment(scenario.accountId());
        allocateAccountCredit(scenario.accountId());

        assertThat(paymentStatusOf(scenario.invoiceId())).isEqualTo("PENDING");
        assertThat(amount("select unallocated_credit_amount from student_current_account where id = ?", scenario.accountId())).isEqualByComparingTo("80000.00");

        decide("reject", requestId, director, 200);

        assertThat(paymentStatusOf(scenario.invoiceId())).isEqualTo("PAID");
        assertThat(amount("select unallocated_credit_amount from student_current_account where id = ?", scenario.accountId())).isEqualByComparingTo("0.00");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void theBackfillLinksTheInvoiceOfEveryPendingCreditNoteRequestThatWasOpenedBeforeTheLink() throws Exception {
        Scenario scenario = unpaidInvoice("80000.00");
        long requestId = pendingRequestId(requestCreditNote(scenario.invoiceId(), newStaff("ADMINISTRATIVO"), creditNoteBody("BILLING_ERROR", "Error", 1, "1000.00"), 202));
        jdbc.update("delete from authorization_request_invoice where authorization_request_id = ?", requestId);
        assertThat(jdbc.queryForObject("select count(*) from authorization_request_invoice where authorization_request_id = ?", Long.class, requestId)).isZero();

        String migration = StreamUtils.copyToString(new ClassPathResource("db/migration/V134__authorization_request_invoice_credit_notes.sql").getInputStream(), StandardCharsets.UTF_8);
        jdbc.execute(migration);
        jdbc.execute(migration);

        assertThat(jdbc.queryForList("select invoice_id from authorization_request_invoice where authorization_request_id = ?", Long.class, requestId))
                .containsExactly(scenario.invoiceId());
    }
}
