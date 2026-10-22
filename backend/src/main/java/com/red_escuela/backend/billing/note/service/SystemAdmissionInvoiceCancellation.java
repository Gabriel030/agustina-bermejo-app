package com.red_escuela.backend.billing.note.service;

import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.api.AdmissionReceiptLookup;
import com.red_escuela.backend.billing.api.AdmissionReceiptSummary;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentStatus;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.invoice.service.InvoiceCreditAccessService;
import com.red_escuela.backend.billing.note.domain.CancelInvoiceCommand;
import com.red_escuela.backend.billing.note.domain.SystemCancellationOrigin;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Component
public class SystemAdmissionInvoiceCancellation {
    public enum Outcome {
        CANCELLED,
        ALREADY_CANCELLED,
        NOT_CANCELLABLE
    }

    private static final Logger log = LoggerFactory.getLogger(SystemAdmissionInvoiceCancellation.class);
    private static final String JUSTIFICATION = "La solicitud de admisión venció al cerrar la admisión sin pagar la matrícula de ingreso";
    private static final String PENDING_RECEIPT = "PENDING_REVIEW";
    private static final Set<InvoicePaymentStatus> UNPAID = Set.of(InvoicePaymentStatus.PENDING, InvoicePaymentStatus.OVERDUE);

    private final InvoiceCreditAccessService invoiceAccess;
    private final StudentCurrentAccountService accountService;
    private final AdmissionReceiptLookup receiptLookup;
    private final BillingOperationPipeline pipeline;
    private final CancelInvoiceOperation cancelInvoiceOperation;
    private final LateFeeAnnulmentOnInstallmentCancellation lateFeeAnnulment;

    public SystemAdmissionInvoiceCancellation(InvoiceCreditAccessService invoiceAccess, StudentCurrentAccountService accountService, AdmissionReceiptLookup receiptLookup,
                                              BillingOperationPipeline pipeline, CancelInvoiceOperation cancelInvoiceOperation,
                                              LateFeeAnnulmentOnInstallmentCancellation lateFeeAnnulment) {
        this.invoiceAccess = invoiceAccess;
        this.accountService = accountService;
        this.receiptLookup = receiptLookup;
        this.pipeline = pipeline;
        this.cancelInvoiceOperation = cancelInvoiceOperation;
        this.lateFeeAnnulment = lateFeeAnnulment;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Outcome cancelWhenUnpaid(Long invoiceId) {
        return cancelWhenUnpaid(invoiceId, SystemCancellationOrigin.ADMISSION_EXPIRED, JUSTIFICATION);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Outcome cancelWhenUnpaid(Long invoiceId, SystemCancellationOrigin origin, String justification) {
        Long accountId = invoiceAccess.findAccountIdOf(invoiceId).orElseThrow(() -> new IllegalStateException("Admission invoice not found: " + invoiceId));
        accountService.lockStudentCurrentAccount(accountId);
        Invoice invoice = invoiceAccess.lockInvoice(invoiceId).orElseThrow(() -> new IllegalStateException("Admission invoice not found: " + invoiceId));
        if (invoice.getDocumentStatus() == InvoiceDocumentStatus.ISSUED && invoice.getPaymentStatus() == InvoicePaymentStatus.CANCELLED) {
            return Outcome.ALREADY_CANCELLED;
        }
        if (invoice.getDocumentStatus() != InvoiceDocumentStatus.ISSUED || !UNPAID.contains(invoice.getPaymentStatus())) {
            return Outcome.NOT_CANCELLABLE;
        }
        AdmissionReceiptSummary receipt = receiptLookup.findLatestReceiptByInvoiceIds(Set.of(invoiceId)).get(invoiceId);
        if ((receipt != null && PENDING_RECEIPT.equals(receipt.status())) || lateFeeAnnulment.hasLateFeeDebitNoteUnderPaymentReview(invoiceId)) {
            return Outcome.NOT_CANCELLABLE;
        }
        pipeline.runOperation(cancelInvoiceOperation, CancelInvoiceCommand.bySystem(invoiceId, origin, justification))
                .resultOrThrowIfAwaitingAuthorization(requestId -> new IllegalStateException("A system cancellation never waits for authorization: " + requestId));
        log.info("Factura de admisión anulada: factura={}, origen={}", invoiceId, origin);
        return Outcome.CANCELLED;
    }
}
