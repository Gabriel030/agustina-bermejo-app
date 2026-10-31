package com.red_escuela.backend.billing.note.service;

import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentStatus;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.invoice.service.InvoiceCreditAccessService;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.enrollment.api.WithdrawalFactsFinder;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("D-311")
class LeftOutInvoiceCancellationOnReceiptRejectionListenerOrderTest {
    private static final Long INVOICE_ID = 11L;
    private static final Long ACCOUNT_ID = 4L;
    private static final Long ENROLLMENT_ID = 5L;

    private final InvoiceCreditAccessService invoices = mock(InvoiceCreditAccessService.class);
    private final WithdrawalInvoiceAccess invoiceAccess = mock(WithdrawalInvoiceAccess.class);
    private final WithdrawalFactsFinder withdrawalFacts = mock(WithdrawalFactsFinder.class);
    private final StudentCurrentAccountService accountService = mock(StudentCurrentAccountService.class);
    private final BillingOperationPipeline pipeline = mock(BillingOperationPipeline.class);
    private final CancelInvoiceOperation operation = mock(CancelInvoiceOperation.class);
    private final LeftOutInvoiceCancellationOnReceiptRejectionListener listener =
            new LeftOutInvoiceCancellationOnReceiptRejectionListener(invoices, invoiceAccess, withdrawalFacts, accountService, pipeline, operation);

    @Test
    void theAccountIsLockedFirstThenTheInvoiceAndOnlyThenTheEnrollmentIsRead() {
        Invoice invoice = mock(Invoice.class);
        when(invoice.getDocumentStatus()).thenReturn(InvoiceDocumentStatus.ISSUED);
        when(invoice.getPaymentStatus()).thenReturn(InvoicePaymentStatus.PENDING);
        when(invoice.findProgramEnrollmentId()).thenReturn(Optional.of(ENROLLMENT_ID));
        when(invoice.findCourseEnrollmentId()).thenReturn(Optional.empty());
        when(invoices.findAccountIdOf(INVOICE_ID)).thenReturn(Optional.of(ACCOUNT_ID));
        when(invoices.lockInvoice(INVOICE_ID)).thenReturn(Optional.of(invoice));
        when(withdrawalFacts.findVoluntaryWithdrawalOfProgramEnrollment(ENROLLMENT_ID)).thenReturn(Optional.empty());

        boolean cancelled = listener.cancelIfLeftOutOfWithdrawal(INVOICE_ID);

        assertThat(cancelled).isFalse();
        InOrder order = inOrder(accountService, invoices, withdrawalFacts);
        order.verify(accountService).lockStudentCurrentAccount(ACCOUNT_ID);
        order.verify(invoices).lockInvoice(INVOICE_ID);
        order.verify(withdrawalFacts).findVoluntaryWithdrawalOfProgramEnrollment(ENROLLMENT_ID);
        verify(pipeline, never()).runOperation(any(CancelInvoiceOperation.class), any());
    }
}
