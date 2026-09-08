package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.entity.PaymentReceipt;
import com.red_escuela.backend.billing.exception.PaymentReceiptNotFoundException;
import com.red_escuela.backend.billing.repository.PaymentReceiptRepository;
import org.springframework.stereotype.Service;

@Service
public class PaymentReceiptFinder {

    private final PaymentReceiptRepository paymentReceiptRepository;

    public PaymentReceiptFinder(PaymentReceiptRepository paymentReceiptRepository) {
        this.paymentReceiptRepository = paymentReceiptRepository;
    }

    public PaymentReceipt findOrThrow(Long id) {
        return paymentReceiptRepository.findById(id).orElseThrow(() -> new PaymentReceiptNotFoundException(id));
    }
}
