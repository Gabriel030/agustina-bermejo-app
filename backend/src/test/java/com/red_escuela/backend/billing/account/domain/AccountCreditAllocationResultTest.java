package com.red_escuela.backend.billing.account.domain;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-089")
class AccountCreditAllocationResultTest {

    @Test
    void nothingAllocatedIsAnEmptyResultWithNoSettledInvoices() {
        AccountCreditAllocationResult result = AccountCreditAllocationResult.nothingAllocated();

        assertThat(result).isNotNull();
        assertThat(result.isEmpty()).isTrue();
        assertThat(result.allocations()).isEmpty();
        assertThat(result.settledInvoices()).isEmpty();
    }

    @Test
    void aResultWithAnAllocationIsNotEmpty() {
        Invoice invoice = AccountTestData.issuedInvoice(1L, "100.00");
        PaymentAllocation allocation = PaymentAllocation.recordFullInvoiceAllocation(invoice, AccountTestData.ars("100.00"), AccountTestData.DAY,
                AccountTestData.DAY, com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.ACTOR);

        assertThat(new AccountCreditAllocationResult(java.util.List.of(allocation), java.util.List.of(invoice)).isEmpty()).isFalse();
    }

    @Test
    void theOpenViewOfAnInvoiceCarriesItsIdentityDatesAndOutstandingAmount() {
        Invoice invoice = AccountTestData.issuedInvoice(7L, "250.00");

        OpenInvoiceView view = OpenInvoiceView.of(invoice);

        assertThat(view).isNotNull();
        assertThat(view.invoiceId()).isEqualTo(7L);
        assertThat(view.documentType()).isEqualTo(InvoiceDocumentType.INVOICE);
        assertThat(view.studentCurrentAccountId()).isEqualTo(AccountTestData.ACCOUNT_ID);
        assertThat(view.outstandingAmount()).isEqualTo(AccountTestData.ars("250.00"));
        assertThat(view.dueDate()).isEqualTo(invoice.getDueDate());
        assertThat(view.issueDate()).isEqualTo(invoice.getIssueDate());
    }
}
