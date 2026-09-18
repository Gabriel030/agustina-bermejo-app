package com.red_escuela.backend.billing.usecase.paymentreceipt;

import com.red_escuela.backend.billing.entity.PaymentReceipt;
import com.red_escuela.backend.billing.entity.PaymentReceiptStatus;
import com.red_escuela.backend.billing.exception.InvalidPaymentReceiptStateException;
import com.red_escuela.backend.billing.repository.PaymentReceiptRepository;
import com.red_escuela.backend.billing.service.PaymentReceiptFinder;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@UseCase
public class RejectPaymentReceiptService implements RejectPaymentReceiptUseCase {
    private final PaymentReceiptRepository paymentReceiptRepository;
    private final PaymentReceiptFinder paymentReceiptFinder;

    public RejectPaymentReceiptService(PaymentReceiptRepository paymentReceiptRepository, PaymentReceiptFinder paymentReceiptFinder) {
        this.paymentReceiptRepository = paymentReceiptRepository;
        this.paymentReceiptFinder = paymentReceiptFinder;
    }

    @Override
    @Transactional
    public PaymentReceipt reject(Long id, String motivo, Person decidedBy) {
        PaymentReceipt receipt = paymentReceiptFinder.findOrThrow(id);
        if (receipt.getStatus() != PaymentReceiptStatus.PENDING_REVIEW) {
            throw new InvalidPaymentReceiptStateException(id, receipt.getStatus().name());
        }
        receipt.setStatus(PaymentReceiptStatus.REJECTED);
        receipt.setRejectionReason(motivo);
        receipt.setReviewedBy(decidedBy);
        receipt.setReviewedAt(LocalDateTime.now());
        return paymentReceiptRepository.save(receipt);
    }
}
