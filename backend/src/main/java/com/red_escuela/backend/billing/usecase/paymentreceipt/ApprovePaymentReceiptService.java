package com.red_escuela.backend.billing.usecase.paymentreceipt;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.entity.BankTransferReceipt;
import com.red_escuela.backend.billing.repository.BankTransferReceiptRepository;
import com.red_escuela.backend.billing.service.PaymentReceiptFinder;
import com.red_escuela.backend.billing.operation.ApprovedPaymentRegistrationPort;
import com.red_escuela.backend.billing.operation.PendingBankTransferReviewPort;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.events.PaymentReceiptAppliedToOlderDebtEvent;
import com.red_escuela.backend.notification.PaymentReceiptApprovedEvent;
import com.red_escuela.backend.shared.UseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;

@UseCase
public class ApprovePaymentReceiptService implements ApprovePaymentReceiptUseCase {
    private static final Logger log = LoggerFactory.getLogger(ApprovePaymentReceiptService.class);
    private final BankTransferReceiptRepository bankTransferReceiptRepository;
    private final PaymentReceiptFinder paymentReceiptFinder;
    private final ApprovedPaymentRegistrationPort registrationPort;
    private final PendingBankTransferReviewPort pendingReviewPort;
    private final ApplicationEventPublisher eventPublisher;

    public ApprovePaymentReceiptService(BankTransferReceiptRepository bankTransferReceiptRepository,
                                         PaymentReceiptFinder paymentReceiptFinder, ApprovedPaymentRegistrationPort registrationPort,
                                         PendingBankTransferReviewPort pendingReviewPort, ApplicationEventPublisher eventPublisher) {
        this.bankTransferReceiptRepository = bankTransferReceiptRepository;
        this.paymentReceiptFinder = paymentReceiptFinder;
        this.registrationPort = registrationPort;
        this.pendingReviewPort = pendingReviewPort;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public BankTransferReceipt approve(Long id, Person decidedBy) {
        BankTransferReceipt receipt = paymentReceiptFinder.findOrThrow(id);
        boolean hasPendingPayment = receipt.hasPendingPayment();
        receipt.approve(decidedBy);

        Invoice invoice = receipt.getInvoice();
        if (hasPendingPayment) {
            pendingReviewPort.approvePendingPayment(receipt.getPaymentId(), paymentReceiptFinder.reviewerIdOf(receipt));
        } else {
            Long paymentId = registrationPort.registerApprovedBankTransferPayment(invoice.getId(), paymentReceiptFinder.reviewerIdOf(receipt));
            paymentReceiptFinder.linkApprovedPayment(receipt, paymentId);
        }
        Person payer = paymentReceiptFinder.payerOf(receipt);

        if (invoice.getPaymentStatus() != InvoicePaymentStatus.PAID) {
            receipt.markAsAppliedToOlderDebt();
            BankTransferReceipt applied = bankTransferReceiptRepository.save(receipt);
            if (payer != null) {
                eventPublisher.publishEvent(PaymentReceiptAppliedToOlderDebtEvent.appliedFor(applied.getId(), invoice.getId(), payer.getId(),
                        invoice.getPeriod().format(DateTimeFormatter.ofPattern("MM/yyyy"))));
            }
            log.info("Comprobante aprobado y aplicado a deuda anterior: comprobante={}, factura={}", id, invoice.getId());
            return applied;
        }

        BankTransferReceipt saved = bankTransferReceiptRepository.save(receipt);
        eventPublisher.publishEvent(new PaymentReceiptApprovedEvent(saved, payer));
        log.info("Comprobante de pago aprobado: comprobante={}, factura={}", id, invoice.getId());
        return saved;
    }
}
