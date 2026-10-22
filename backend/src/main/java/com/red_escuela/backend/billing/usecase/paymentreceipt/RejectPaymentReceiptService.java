package com.red_escuela.backend.billing.usecase.paymentreceipt;

import com.red_escuela.backend.billing.entity.BankTransferReceipt;
import com.red_escuela.backend.billing.repository.BankTransferReceiptRepository;
import com.red_escuela.backend.billing.service.PaymentReceiptFinder;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.UseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class RejectPaymentReceiptService implements RejectPaymentReceiptUseCase {
    private static final Logger log = LoggerFactory.getLogger(RejectPaymentReceiptService.class);
    private final BankTransferReceiptRepository bankTransferReceiptRepository;
    private final PaymentReceiptFinder paymentReceiptFinder;

    public RejectPaymentReceiptService(BankTransferReceiptRepository bankTransferReceiptRepository, PaymentReceiptFinder paymentReceiptFinder) {
        this.bankTransferReceiptRepository = bankTransferReceiptRepository;
        this.paymentReceiptFinder = paymentReceiptFinder;
    }

    @Override
    @Transactional
    public BankTransferReceipt reject(Long id, String motivo, Person decidedBy) {
        BankTransferReceipt receipt = paymentReceiptFinder.findOrThrow(id);
        boolean hasPendingPayment = paymentReceiptFinder.hasPendingPayment(receipt);
        receipt.reject(decidedBy, motivo);
        if (hasPendingPayment) {
            paymentReceiptFinder.rejectPendingPayment(receipt, motivo);
        }
        BankTransferReceipt saved = bankTransferReceiptRepository.save(receipt);
        paymentReceiptFinder.publishRejected(saved);
        log.warn("Comprobante de pago rechazado: comprobante={}, factura={}", id, saved.getInvoice().getId());
        return saved;
    }
}
