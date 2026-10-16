package com.red_escuela.backend.billing.note.service;

import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.billing.account.service.CurrentAccountAllocationService;
import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.SpyBean;

import java.time.LocalDate;

import static com.red_escuela.backend.billing.account.domain.AccountTestData.ars;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

@Tag("D-313")
@Tag("D-253")
class ResumeRejectedAllocationIT extends AbstractInvoiceCancellationIT {

    @SpyBean
    private CurrentAccountAllocationService spiedAllocation;

    @AfterEach
    void resetSpy() {
        Mockito.reset(spiedAllocation);
    }

    private record Setup(Scenario cancelled, Long otherInvoiceId, long requestId, Staff director) {
    }

    private Setup requestAlreadyDoomedWithCreditWaiting() throws Exception {
        Scenario scenario = unpaidInvoice("80000.00");
        Long otherInvoiceId = issueInvoiceOfStudent(scenario.studentId(), scenario.accountId(), "80000.00", DUE_DATE.plusDays(30));
        Staff administrative = newStaff("ADMINISTRATIVO");
        Staff director = newStaff("DIRECTOR");
        long requestId = pendingRequestId(requestCancellation(scenario.invoiceId(), administrative, cancellationBody("CANCELLATION", "Baja"), 202));
        requestCancellation(scenario.invoiceId(), director, cancellationBody("CANCELLATION", "Baja directa"), 201);
        inTransaction(() -> {
            service.recordPaymentReceivedCredit(service.lockStudentCurrentAccount(scenario.accountId()), BillingTestFixtures.seedApprovedPaymentRow(scenario.accountId()), ars("80000.00"), LocalDate.of(2027, 3, 11), "Pago");
            return null;
        });
        assertThat(paymentStatusOf(otherInvoiceId)).isEqualTo("PENDING");
        return new Setup(scenario, otherInvoiceId, requestId, director);
    }

    @Test
    @Tag("B2-38")
    void aRequestRejectedOnResumeAllocatesTheAccountCredit() throws Exception {
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Setup setup = requestAlreadyDoomedWithCreditWaiting();

        decide("approve", setup.requestId(), setup.director(), 409);

        assertThat(jdbc.queryForMap("select status, resume_outcome from authorization_request where id = ?", setup.requestId()))
                .containsEntry("status", "APPROVED").containsEntry("resume_outcome", "REJECTED_ON_RESUME");
        assertThat(paymentStatusOf(setup.otherInvoiceId())).isEqualTo("PAID");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("B2-39")
    void aFailingAllocationDoesNotPreventTheRejectionOnResume() throws Exception {
        Setup setup = requestAlreadyDoomedWithCreditWaiting();
        inTransaction(() -> {
            Mockito.doThrow(new IllegalStateException("allocation failed")).when(spiedAllocation).allocateAccountCreditToOldestInvoices(any(), any(), any());
            return null;
        });

        decide("approve", setup.requestId(), setup.director(), 409);

        assertThat(jdbc.queryForMap("select status, resume_outcome from authorization_request where id = ?", setup.requestId()))
                .containsEntry("status", "APPROVED").containsEntry("resume_outcome", "REJECTED_ON_RESUME");
        assertThat(paymentStatusOf(setup.otherInvoiceId())).isEqualTo("PENDING");
    }
}
