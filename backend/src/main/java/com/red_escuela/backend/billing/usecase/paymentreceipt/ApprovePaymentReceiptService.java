package com.red_escuela.backend.billing.usecase.paymentreceipt;

import com.red_escuela.backend.billing.entity.BankTransferReceipt;
import com.red_escuela.backend.billing.events.PaymentReceiptAppliedToOlderDebtEvent;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.operation.ApprovedPaymentRegistrationPort;
import com.red_escuela.backend.billing.operation.PaymentVerification;
import com.red_escuela.backend.billing.operation.PendingTransferApprovalAssessment;
import com.red_escuela.backend.billing.operation.PendingTransferApprovalOutcome;
import com.red_escuela.backend.billing.payment.exception.LegacyReceiptHasNoVerifiablePaymentException;
import com.red_escuela.backend.billing.payment.exception.PaymentReceiptAlreadyReviewedException;
import com.red_escuela.backend.billing.payment.exception.ReceiptTransferDateMismatchException;
import com.red_escuela.backend.billing.repository.BankTransferReceiptRepository;
import com.red_escuela.backend.billing.service.PaymentReceiptFinder;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.UseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;

@UseCase
public class ApprovePaymentReceiptService implements ApprovePaymentReceiptUseCase {
    private static final Logger log = LoggerFactory.getLogger(ApprovePaymentReceiptService.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final BankTransferReceiptRepository bankTransferReceiptRepository;
    private final PaymentReceiptFinder paymentReceiptFinder;
    private final ApprovedPaymentRegistrationPort registrationPort;
    private final ApplicationEventPublisher eventPublisher;

    public ApprovePaymentReceiptService(BankTransferReceiptRepository bankTransferReceiptRepository,
                                         PaymentReceiptFinder paymentReceiptFinder, ApprovedPaymentRegistrationPort registrationPort,
                                         ApplicationEventPublisher eventPublisher) {
        this.bankTransferReceiptRepository = bankTransferReceiptRepository;
        this.paymentReceiptFinder = paymentReceiptFinder;
        this.registrationPort = registrationPort;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional(noRollbackFor = ReceiptTransferDateMismatchException.class)
    public BankTransferReceipt approve(Long id, Person decidedBy) {
        return approve(id, decidedBy, PaymentVerification.none());
    }

    @Override
    @Transactional(noRollbackFor = ReceiptTransferDateMismatchException.class)
    public BankTransferReceipt approve(Long id, Person decidedBy, PaymentVerification verification) {
        BankTransferReceipt receipt = paymentReceiptFinder.findOrThrow(id);
        boolean hasPendingPayment = paymentReceiptFinder.hasPendingPayment(receipt);
        if (receipt.isPendingReview() && receipt.getPaymentId() == null && !verification.isEmpty()) {
            throw new LegacyReceiptHasNoVerifiablePaymentException();
        }
        if (hasPendingPayment) {
            PendingTransferApprovalAssessment assessment = paymentReceiptFinder.assessApproval(receipt, verification);
            if (!assessment.transferDateMatchesUploadDate()) {
                rejectBecauseTransferDateDiffersFromUploadDate(receipt, decidedBy, assessment);
            }
        }
        receipt.approve(decidedBy);
        flushReviewOf(receipt);

        Invoice invoice = receipt.getInvoice();
        boolean appliedToOlderDebt;
        if (hasPendingPayment) {
            PendingTransferApprovalOutcome outcome = paymentReceiptFinder.approvePendingPayment(receipt, verification);
            appliedToOlderDebt = outcome.settledAnInvoiceOutsideTheReceipt();
        } else {
            Long paymentId = registrationPort.registerApprovedBankTransferPayment(invoice.getId(), paymentReceiptFinder.reviewerIdOf(receipt));
            paymentReceiptFinder.linkApprovedPayment(receipt, paymentId);
            appliedToOlderDebt = invoice.getPaymentStatus() != InvoicePaymentStatus.PAID;
        }
        Long payerId = paymentReceiptFinder.payerIdOf(receipt);

        if (appliedToOlderDebt) {
            receipt.markAsAppliedToOlderDebt();
            BankTransferReceipt applied = bankTransferReceiptRepository.save(receipt);
            if (payerId != null) {
                eventPublisher.publishEvent(PaymentReceiptAppliedToOlderDebtEvent.appliedFor(applied.getId(), invoice.getId(), payerId,
                        invoice.getPeriod().format(DateTimeFormatter.ofPattern("MM/yyyy"))));
            }
            log.info("Comprobante aprobado y aplicado a deuda anterior: comprobante={}, factura={}", id, invoice.getId());
            return applied;
        }

        BankTransferReceipt saved = bankTransferReceiptRepository.save(receipt);
        paymentReceiptFinder.publishApproved(saved);
        log.info("Comprobante de pago aprobado: comprobante={}, factura={}", id, invoice.getId());
        return saved;
    }

    private void rejectBecauseTransferDateDiffersFromUploadDate(BankTransferReceipt receipt, Person decidedBy, PendingTransferApprovalAssessment assessment) {
        String reason = "La fecha de la transferencia (" + assessment.verifiedTransferDate().format(DATE_FORMAT)
                + ") no coincide con la fecha en que se subió el comprobante (" + assessment.uploadDate().format(DATE_FORMAT) + ")";
        receipt.reject(decidedBy, reason);
        flushReviewOf(receipt);
        paymentReceiptFinder.rejectPendingPayment(receipt, reason);
        BankTransferReceipt rejected = bankTransferReceiptRepository.save(receipt);
        paymentReceiptFinder.publishRejected(rejected);
        log.warn("Comprobante rechazado por fecha de transferencia distinta de la de subida: comprobante={}, fechaDeSubida={}, fechaVerificada={}", receipt.getId(),
                assessment.uploadDate(), assessment.verifiedTransferDate());
        throw new ReceiptTransferDateMismatchException(assessment.uploadDate(), assessment.verifiedTransferDate());
    }

    private void flushReviewOf(BankTransferReceipt receipt) {
        try {
            bankTransferReceiptRepository.saveAndFlush(receipt);
        } catch (ObjectOptimisticLockingFailureException concurrentReview) {
            throw new PaymentReceiptAlreadyReviewedException();
        }
    }
}
