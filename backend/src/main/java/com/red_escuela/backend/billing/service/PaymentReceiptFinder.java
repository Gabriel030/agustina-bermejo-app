package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.entity.BankTransferReceipt;
import com.red_escuela.backend.billing.exception.PaymentReceiptNotFoundException;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.repository.BankTransferReceiptRepository;
import com.red_escuela.backend.billing.operation.PaymentVerification;
import com.red_escuela.backend.billing.operation.PendingBankTransferReviewPort;
import com.red_escuela.backend.billing.operation.PendingTransferApprovalAssessment;
import com.red_escuela.backend.billing.operation.PendingTransferApprovalOutcome;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.notification.PaymentReceiptApprovedEvent;
import com.red_escuela.backend.notification.PaymentReceiptRejectedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PaymentReceiptFinder {

    private final BankTransferReceiptRepository bankTransferReceiptRepository;
    private final InvoiceRepository invoiceRepository;
    private final PendingBankTransferReviewPort pendingReviewPort;
    private final ApplicationEventPublisher eventPublisher;

    public PaymentReceiptFinder(BankTransferReceiptRepository bankTransferReceiptRepository, InvoiceRepository invoiceRepository,
                                PendingBankTransferReviewPort pendingReviewPort, ApplicationEventPublisher eventPublisher) {
        this.bankTransferReceiptRepository = bankTransferReceiptRepository;
        this.invoiceRepository = invoiceRepository;
        this.pendingReviewPort = pendingReviewPort;
        this.eventPublisher = eventPublisher;
    }

    public BankTransferReceipt findOrThrow(Long id) {
        return bankTransferReceiptRepository.findById(id).orElseThrow(() -> new PaymentReceiptNotFoundException(id));
    }

    public Long reviewerIdOf(BankTransferReceipt receipt) {
        return receipt.getReviewedBy().getId();
    }

    public Person payerOf(BankTransferReceipt receipt) {
        return invoiceRepository.findOwnerPersonByInvoiceId(receipt.getInvoice().getId()).orElse(null);
    }

    public Long payerIdOf(BankTransferReceipt receipt) {
        Person payer = payerOf(receipt);
        return payer == null ? null : payer.getId();
    }

    public boolean hasPendingPayment(BankTransferReceipt receipt) {
        return receipt.hasPendingPayment();
    }

    public PendingTransferApprovalAssessment assessApproval(BankTransferReceipt receipt, PaymentVerification verification) {
        return pendingReviewPort.assessApproval(receipt.getPaymentId(), verification);
    }

    public PendingTransferApprovalOutcome approvePendingPayment(BankTransferReceipt receipt, PaymentVerification verification) {
        return pendingReviewPort.approvePendingPayment(receipt.getPaymentId(), reviewerIdOf(receipt), verification);
    }

    public List<Long> rejectPendingPayment(BankTransferReceipt receipt, String reason) {
        return pendingReviewPort.rejectPendingPayment(receipt.getPaymentId(), reason, reviewerIdOf(receipt));
    }

    public void publishApproved(BankTransferReceipt receipt) {
        eventPublisher.publishEvent(new PaymentReceiptApprovedEvent(receipt, payerOf(receipt)));
    }

    public void publishRejected(BankTransferReceipt receipt) {
        eventPublisher.publishEvent(new PaymentReceiptRejectedEvent(receipt, payerOf(receipt)));
    }

    public void linkApprovedPayment(BankTransferReceipt receipt, Long paymentId) {
        receipt.setPaymentId(paymentId);
    }
}
