package com.red_escuela.backend.billing.payment.service;

import com.red_escuela.backend.billing.entity.BankTransferReceipt;
import com.red_escuela.backend.billing.events.PaymentReceiptAppliedToOlderDebtEvent;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.operation.ApprovedPaymentRegistrationPort;
import com.red_escuela.backend.billing.operation.PaymentVerification;
import com.red_escuela.backend.billing.operation.PendingTransferApprovalAssessment;
import com.red_escuela.backend.billing.operation.PendingTransferApprovalOutcome;
import com.red_escuela.backend.billing.payment.exception.ReceiptTransferDateMismatchException;
import com.red_escuela.backend.billing.payment.policy.PaymentRejectionCheck;
import com.red_escuela.backend.billing.payment.policy.ReceiptApprovalCheck;
import com.red_escuela.backend.billing.payment.policy.PaymentReviewRejections;
import com.red_escuela.backend.billing.operation.BankTransferReceiptReviewPort;
import com.red_escuela.backend.billing.repository.BankTransferReceiptRepository;
import com.red_escuela.backend.billing.service.PaymentReceiptFinder;
import com.red_escuela.backend.shared.domain.policy.PolicyEvaluationResult;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import com.red_escuela.backend.identity.entity.Person;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class BankTransferReceiptReviewer implements BankTransferReceiptReviewPort {
    private static final Logger log = LoggerFactory.getLogger(BankTransferReceiptReviewer.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final BankTransferReceiptRepository bankTransferReceiptRepository;
    private final PaymentReceiptFinder paymentReceiptFinder;
    private final ApprovedPaymentRegistrationPort registrationPort;
    private final ApplicationEventPublisher eventPublisher;
    private final RejectedCourseReceiptCancellation rejectedCourseReceiptCancellation;
    private final PolicySet<PaymentRejectionCheck> rejectionReasonPolicies;
    private final PolicySet<ReceiptApprovalCheck> approvalPolicies;

    public BankTransferReceiptReviewer(BankTransferReceiptRepository bankTransferReceiptRepository,
                                         PaymentReceiptFinder paymentReceiptFinder, ApprovedPaymentRegistrationPort registrationPort,
                                         ApplicationEventPublisher eventPublisher, RejectedCourseReceiptCancellation rejectedCourseReceiptCancellation,
                                       PolicySet<PaymentRejectionCheck> paymentRejectionReasonPolicySet,
                                       PolicySet<ReceiptApprovalCheck> receiptApprovalPolicySet) {
        this.bankTransferReceiptRepository = bankTransferReceiptRepository;
        this.paymentReceiptFinder = paymentReceiptFinder;
        this.registrationPort = registrationPort;
        this.eventPublisher = eventPublisher;
        this.rejectedCourseReceiptCancellation = rejectedCourseReceiptCancellation;
        this.rejectionReasonPolicies = paymentRejectionReasonPolicySet;
        this.approvalPolicies = receiptApprovalPolicySet;
    }

    @Override
    @Transactional(noRollbackFor = ReceiptTransferDateMismatchException.class)
    public BankTransferReceipt approveReceipt(Long id, Person decidedBy, PaymentVerification verification) {
        BankTransferReceipt receipt = paymentReceiptFinder.findOrThrow(id);
        boolean hasPendingPayment = paymentReceiptFinder.hasPendingPayment(receipt);
        PendingTransferApprovalAssessment assessment = hasPendingPayment ? paymentReceiptFinder.assessApproval(receipt, verification) : null;
        PolicyEvaluationResult approvalCheck = approvalPolicies.evaluateRulesUntilFirstViolation(new ReceiptApprovalCheck(
                receipt.isPendingReview() && receipt.getPaymentId() == null, !verification.isEmpty(),
                assessment == null ? null : assessment.uploadDate(), assessment == null ? null : assessment.verifiedTransferDate()));
        if (!approvalCheck.allowed()) {
            if (assessment != null && !assessment.transferDateMatchesUploadDate()) {
                rejectBecauseTransferDateDiffersFromUploadDate(receipt, decidedBy, assessment);
            }
            approvalCheck.throwIfDenied(PaymentReviewRejections::rejectionOf);
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

    @Override
    @Transactional
    public BankTransferReceipt rejectReceipt(Long id, String reason, Person decidedBy) {
        rejectionReasonPolicies.evaluateRulesUntilFirstViolation(new PaymentRejectionCheck(reason)).throwIfDenied(PaymentReviewRejections::rejectionOf);
        BankTransferReceipt receipt = paymentReceiptFinder.findOrThrow(id);
        boolean hasPendingPayment = paymentReceiptFinder.hasPendingPayment(receipt);
        receipt.reject(decidedBy, reason);
        flushReviewOf(receipt);
        List<Long> receiptItemInvoiceIds = hasPendingPayment ? paymentReceiptFinder.rejectPendingPayment(receipt, reason) : List.of(receipt.getInvoice().getId());
        rejectedCourseReceiptCancellation.cancelCourseAdmissionsOfRejectedReceipt(receiptItemInvoiceIds);
        BankTransferReceipt saved = bankTransferReceiptRepository.save(receipt);
        paymentReceiptFinder.publishRejected(saved);
        log.warn("Comprobante de pago rechazado: comprobante={}, factura={}", id, saved.getInvoice().getId());
        return saved;
    }

    private void rejectBecauseTransferDateDiffersFromUploadDate(BankTransferReceipt receipt, Person decidedBy, PendingTransferApprovalAssessment assessment) {
        String reason = "La fecha de la transferencia (" + assessment.verifiedTransferDate().format(DATE_FORMAT)
                + ") no coincide con la fecha en que se subió el comprobante (" + assessment.uploadDate().format(DATE_FORMAT) + ")";
        receipt.rejectBecauseTransferDateDiffersFromUploadDate(decidedBy, reason);
        flushReviewOf(receipt);
        List<Long> receiptItemInvoiceIds = paymentReceiptFinder.rejectPendingPayment(receipt, reason);
        rejectedCourseReceiptCancellation.cancelCourseAdmissionsOfRejectedReceipt(receiptItemInvoiceIds);
        BankTransferReceipt rejected = bankTransferReceiptRepository.save(receipt);
        paymentReceiptFinder.publishRejected(rejected);
        log.warn("Comprobante rechazado por fecha de transferencia distinta de la de subida: comprobante={}, fechaDeSubida={}, fechaVerificada={}", receipt.getId(),
                assessment.uploadDate(), assessment.verifiedTransferDate());
    }

    private void flushReviewOf(BankTransferReceipt receipt) {
        try {
            bankTransferReceiptRepository.saveAndFlush(receipt);
        } catch (ObjectOptimisticLockingFailureException concurrentReview) {
            throw PaymentReviewRejections.concurrentReviewDetected();
        }
    }
}
