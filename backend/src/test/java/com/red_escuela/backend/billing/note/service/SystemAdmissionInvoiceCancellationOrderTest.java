package com.red_escuela.backend.billing.note.service;

import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.api.AdmissionReceiptLookup;
import com.red_escuela.backend.billing.api.AdmissionReceiptSummary;
import com.red_escuela.backend.billing.api.ExpiredAdmissionInvoiceCancellation.Outcome;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentStatus;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.note.domain.CreditNote;
import com.red_escuela.backend.billing.operation.BillingOperationOutcome;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("D-229")
@Tag("D-252")
class SystemAdmissionInvoiceCancellationOrderTest {
    private static final Long INVOICE_ID = 7L;
    private static final Long ACCOUNT_ID = 3L;

    private final WithdrawalInvoiceAccess invoiceAccess = mock(WithdrawalInvoiceAccess.class);
    private final StudentCurrentAccountService accountService = mock(StudentCurrentAccountService.class);
    private final AdmissionReceiptLookup receiptLookup = mock(AdmissionReceiptLookup.class);
    private final BillingOperationPipeline pipeline = mock(BillingOperationPipeline.class);
    private final CancelInvoiceOperation operation = mock(CancelInvoiceOperation.class);
    private final SystemAdmissionInvoiceCancellation cancellation = new SystemAdmissionInvoiceCancellation(invoiceAccess, accountService, receiptLookup, pipeline, operation);

    private Invoice unpaidInvoice() {
        Invoice invoice = mock(Invoice.class);
        when(invoice.getDocumentStatus()).thenReturn(InvoiceDocumentStatus.ISSUED);
        when(invoice.getPaymentStatus()).thenReturn(InvoicePaymentStatus.OVERDUE);
        when(invoiceAccess.findAccountIdOfInvoice(INVOICE_ID)).thenReturn(Optional.of(ACCOUNT_ID));
        when(invoiceAccess.lockInvoice(INVOICE_ID)).thenReturn(Optional.of(invoice));
        return invoice;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void pipelineReturnsACreditNote() {
        when(pipeline.runOperation(any(CancelInvoiceOperation.class), any())).thenReturn((BillingOperationOutcome) new BillingOperationOutcome.Processed<>(mock(CreditNote.class)));
    }

    @Test
    void theAccountIsLockedFirstThenTheInvoiceThenTheReceiptsAreReadAndOnlyThenTheInvoiceIsCancelled() {
        unpaidInvoice();
        when(receiptLookup.findLatestReceiptByInvoiceIds(Set.of(INVOICE_ID))).thenReturn(Map.of());
        pipelineReturnsACreditNote();

        Outcome outcome = cancellation.cancelWhenUnpaid(INVOICE_ID);

        assertThat(outcome).isEqualTo(Outcome.CANCELLED);
        InOrder order = inOrder(accountService, invoiceAccess, receiptLookup, pipeline);
        order.verify(accountService).lockStudentCurrentAccount(ACCOUNT_ID);
        order.verify(invoiceAccess).lockInvoice(INVOICE_ID);
        order.verify(receiptLookup).findLatestReceiptByInvoiceIds(Set.of(INVOICE_ID));
        order.verify(pipeline).runOperation(any(CancelInvoiceOperation.class), any());
    }

    @Test
    void aPendingReceiptFoundAfterLockingTheInvoiceLeavesTheInvoiceAlone() {
        unpaidInvoice();
        when(receiptLookup.findLatestReceiptByInvoiceIds(Set.of(INVOICE_ID)))
                .thenReturn(Map.of(INVOICE_ID, new AdmissionReceiptSummary("PENDING_REVIEW", LocalDateTime.of(2027, 4, 1, 10, 0))));

        assertThat(cancellation.cancelWhenUnpaid(INVOICE_ID)).isEqualTo(Outcome.NOT_CANCELLABLE);

        verify(pipeline, never()).runOperation(any(CancelInvoiceOperation.class), any());
        InOrder order = inOrder(invoiceAccess, receiptLookup);
        order.verify(invoiceAccess).lockInvoice(INVOICE_ID);
        order.verify(receiptLookup).findLatestReceiptByInvoiceIds(Set.of(INVOICE_ID));
    }
}
