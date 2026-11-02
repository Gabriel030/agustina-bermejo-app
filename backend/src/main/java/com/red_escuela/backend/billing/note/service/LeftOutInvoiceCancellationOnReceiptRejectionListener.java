package com.red_escuela.backend.billing.note.service;

import com.red_escuela.backend.shared.module.BillingMode;
import com.red_escuela.backend.shared.module.RequiresModule;
import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentStatus;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.invoice.domain.event.InvoicePaymentReviewReleasedEvent;
import com.red_escuela.backend.billing.invoice.service.InvoiceCreditAccessService;
import com.red_escuela.backend.billing.note.domain.CancelInvoiceCommand;
import com.red_escuela.backend.billing.note.domain.SystemCancellationOrigin;
import com.red_escuela.backend.billing.note.domain.WithdrawalCancellationPlanner;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.enrollment.api.WithdrawalFactsFinder;
import com.red_escuela.backend.enrollment.api.WithdrawnEnrollmentFacts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class LeftOutInvoiceCancellationOnReceiptRejectionListener {
    private static final Logger log = LoggerFactory.getLogger(LeftOutInvoiceCancellationOnReceiptRejectionListener.class);
    private static final String JUSTIFICATION = "Baja: el comprobante de la matrícula del año siguiente fue rechazado";
    private static final Set<InvoicePaymentStatus> UNPAID = Set.of(InvoicePaymentStatus.PENDING, InvoicePaymentStatus.OVERDUE);

    private final InvoiceCreditAccessService invoices;
    private final WithdrawalInvoiceAccess invoiceAccess;
    private final WithdrawalFactsFinder withdrawalFacts;
    private final StudentCurrentAccountService accountService;
    private final BillingOperationPipeline pipeline;
    private final CancelInvoiceOperation cancelInvoiceOperation;

    public LeftOutInvoiceCancellationOnReceiptRejectionListener(InvoiceCreditAccessService invoices, WithdrawalInvoiceAccess invoiceAccess, WithdrawalFactsFinder withdrawalFacts,
                                                         StudentCurrentAccountService accountService, BillingOperationPipeline pipeline,
                                                         CancelInvoiceOperation cancelInvoiceOperation) {
        this.invoices = invoices;
        this.invoiceAccess = invoiceAccess;
        this.withdrawalFacts = withdrawalFacts;
        this.accountService = accountService;
        this.pipeline = pipeline;
        this.cancelInvoiceOperation = cancelInvoiceOperation;
    }

    @RequiresModule(value = RequiresModule.BILLING, minMode = BillingMode.FULL)
    @ApplicationModuleListener
    public void cancelInvoiceLeftOutOfWithdrawalWhoseReceiptWasRejected(InvoicePaymentReviewReleasedEvent event) {
        cancelIfLeftOutOfWithdrawal(event.invoiceId());
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public boolean cancelIfLeftOutOfWithdrawal(Long invoiceId) {
        Optional<Long> accountId = invoices.findAccountIdOf(invoiceId);
        if (accountId.isEmpty()) {
            return false;
        }
        accountService.lockStudentCurrentAccount(accountId.get());
        Optional<Invoice> locked = invoices.lockInvoice(invoiceId).filter(this::isStillOpenAndUnpaid);
        if (locked.isEmpty()) {
            return false;
        }
        Invoice invoice = locked.get();
        Optional<WithdrawnEnrollmentFacts> found = findVoluntaryWithdrawalOf(invoice);
        if (found.isEmpty()) {
            return false;
        }
        WithdrawnEnrollmentFacts withdrawal = found.get();
        Set<Long> withPendingRequest = invoiceAccess.findInvoiceIdsWithPendingAuthorizationRequest(List.of(invoiceId));
        boolean automatic = new WithdrawalCancellationPlanner(YearMonth.from(withdrawal.effectiveDate()), withdrawal.registeredOn(), invoiceAccess::findTermStartOfTemplate)
                .planFor(List.of(invoice), withPendingRequest).automaticInvoiceIds().contains(invoiceId);
        if (!automatic) {
            return false;
        }
        pipeline.runOperation(cancelInvoiceOperation,
                CancelInvoiceCommand.bySystem(invoiceId, SystemCancellationOrigin.REJECTED_RECEIPT_OF_INVOICE_LEFT_OUT_OF_WITHDRAWAL, JUSTIFICATION));
        log.info("Factura que quedó fuera de la baja anulada al rechazarse su comprobante: factura={}, inscripcionPrograma={}, inscripcionCurso={}", invoiceId,
                withdrawal.programEnrollmentId(), withdrawal.courseEnrollmentId());
        return true;
    }

    private boolean isStillOpenAndUnpaid(Invoice invoice) {
        return invoice.getDocumentStatus() == InvoiceDocumentStatus.ISSUED && UNPAID.contains(invoice.getPaymentStatus());
    }

    private Optional<WithdrawnEnrollmentFacts> findVoluntaryWithdrawalOf(Invoice invoice) {
        Optional<WithdrawnEnrollmentFacts> ofProgram = invoice.findProgramEnrollmentId().flatMap(withdrawalFacts::findVoluntaryWithdrawalOfProgramEnrollment);
        if (ofProgram.isPresent()) {
            return ofProgram;
        }
        return invoice.findCourseEnrollmentId().flatMap(withdrawalFacts::findVoluntaryWithdrawalOfCourseEnrollment);
    }
}
