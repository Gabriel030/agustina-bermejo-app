package com.red_escuela.backend.billing.account.service;

import com.red_escuela.backend.billing.run.domain.BillingRunStepOutcome;
import com.red_escuela.backend.billing.run.operation.AccountCreditAllocationBillingRunStep;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.shared.domain.Currency;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static com.red_escuela.backend.billing.account.domain.AccountTestData.ars;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-451")
class AccountCreditAllocationBillingRunStepIT extends AbstractCurrentAccountIT {

    private static final LocalDate NIGHT = LocalDate.of(2027, 5, 7);

    @Autowired
    private AccountCreditAllocationBillingRunStep step;

    private Long accountWithCreditAndTwoInvoices(String credit, Long[] invoiceIds) {
        Long accountId = newAccountId(newStudentId(), Currency.ARS);
        invoiceIds[0] = issueAdministrativeFeeWithLedgerDebit(accountId, "80000.00", LocalDate.of(2027, 4, 10));
        invoiceIds[1] = issueAdministrativeFeeWithLedgerDebit(accountId, "80000.00", LocalDate.of(2027, 3, 10));
        inTransaction(() -> {
            service.recordPaymentReceivedCredit(service.lockStudentCurrentAccount(accountId), BillingTestFixtures.seedApprovedPaymentRow(accountId, ars(credit)),
                    ars(credit), LocalDate.of(2027, 5, 1), "Pago");
            return null;
        });
        return accountId;
    }

    @Test
    void theNightSettlesTheMostOverdueInvoiceFirstAndOnlyWholeInvoices() {
        Long[] invoices = new Long[2];
        Long accountId = accountWithCreditAndTwoInvoices("100000.00", invoices);

        BillingRunStepOutcome outcome = step.runStepForBusinessDate(NIGHT);

        assertThat(outcome.failed()).isZero();
        assertThat(jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, invoices[1])).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, invoices[0])).isNotEqualTo("PAID");
        assertThat(amount("select unallocated_credit_amount from student_current_account where id = ?", accountId)).isEqualByComparingTo("20000.00");
    }

    @Test
    void runningTheStepTwiceInTheSameNightDoesNotAllocateAgain() {
        Long[] invoices = new Long[2];
        Long accountId = accountWithCreditAndTwoInvoices("100000.00", invoices);
        step.runStepForBusinessDate(NIGHT);
        int allocationsAfterFirstRun = jdbc.queryForObject("select count(*) from payment_allocation where invoice_id in (?, ?)", Integer.class, invoices[0], invoices[1]);

        step.runStepForBusinessDate(NIGHT);

        assertThat(jdbc.queryForObject("select count(*) from payment_allocation where invoice_id in (?, ?)", Integer.class, invoices[0], invoices[1])).isEqualTo(allocationsAfterFirstRun);
        assertThat(amount("select unallocated_credit_amount from student_current_account where id = ?", accountId)).isEqualByComparingTo("20000.00");
    }
}
