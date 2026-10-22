package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.entity.BankTransferReceiptStatus;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.planning.domain.ObligationInvoiceFacts;
import com.red_escuela.backend.billing.repository.BankTransferReceiptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.issue;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.monthlyDraft;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceTestData.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("D-153")
class EnrollmentFeeRequirementInvoiceFactsTest {

    private static final long INVOICE_ID = 15L;

    private BankTransferReceiptRepository receipts;
    private EnrollmentFeeRequirementService service;
    private Invoice invoice;

    @BeforeEach
    void createTheServiceWithOnlyTheReceiptRepository() {
        receipts = mock(BankTransferReceiptRepository.class);
        service = new EnrollmentFeeRequirementService(null, null, receipts, null, null, null, null, null);
        invoice = withId(monthlyDraft("80000.00"), INVOICE_ID);
        issue(invoice, LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 10));
    }

    @Test
    void aReceiptOfTheNewFlowCountsAsAReceiptUnderReview() {
        when(receipts.existsByCheckoutItemInvoiceIdAndStatus(INVOICE_ID, BankTransferReceiptStatus.PENDING_REVIEW)).thenReturn(true);

        ObligationInvoiceFacts facts = service.invoiceFactsOf(invoice);

        assertThat(facts.receiptUnderReview()).isTrue();
        assertThat(facts.status()).isEqualTo(InvoicePaymentStatus.PENDING);
        assertThat(facts.untouched()).isTrue();
    }

    @Test
    void aLegacyReceiptCountsAsAReceiptUnderReview() {
        when(receipts.existsByInvoiceIdAndStatus(INVOICE_ID, BankTransferReceiptStatus.PENDING_REVIEW)).thenReturn(true);

        assertThat(service.invoiceFactsOf(invoice).receiptUnderReview()).isTrue();
    }

    @Test
    void withoutAnyPendingReceiptNothingIsUnderReview() {
        assertThat(service.invoiceFactsOf(invoice).receiptUnderReview()).isFalse();
    }

    @Test
    void aReceiptWithAnotherStatusDoesNotCountAsUnderReview() {
        when(receipts.existsByCheckoutItemInvoiceIdAndStatus(INVOICE_ID, BankTransferReceiptStatus.APPROVED)).thenReturn(true);
        when(receipts.existsByInvoiceIdAndStatus(INVOICE_ID, BankTransferReceiptStatus.REJECTED)).thenReturn(true);

        assertThat(service.invoiceFactsOf(invoice).receiptUnderReview()).isFalse();
    }
}
