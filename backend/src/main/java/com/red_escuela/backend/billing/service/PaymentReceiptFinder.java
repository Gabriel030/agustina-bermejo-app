package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.entity.BankTransferReceipt;
import com.red_escuela.backend.billing.exception.PaymentReceiptNotFoundException;
import com.red_escuela.backend.billing.repository.BankTransferReceiptRepository;
import org.springframework.stereotype.Service;

@Service
public class PaymentReceiptFinder {

    private final BankTransferReceiptRepository bankTransferReceiptRepository;

    public PaymentReceiptFinder(BankTransferReceiptRepository bankTransferReceiptRepository) {
        this.bankTransferReceiptRepository = bankTransferReceiptRepository;
    }

    public BankTransferReceipt findOrThrow(Long id) {
        return bankTransferReceiptRepository.findById(id).orElseThrow(() -> new PaymentReceiptNotFoundException(id));
    }

    public Long reviewerIdOf(BankTransferReceipt receipt) {
        return receipt.getReviewedBy().getId();
    }

    public void linkApprovedPayment(BankTransferReceipt receipt, Long paymentId) {
        receipt.setPaymentId(paymentId);
    }
}
