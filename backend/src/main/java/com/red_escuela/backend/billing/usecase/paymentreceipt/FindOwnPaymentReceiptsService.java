package com.red_escuela.backend.billing.usecase.paymentreceipt;

import com.red_escuela.backend.billing.entity.PaymentReceipt;
import com.red_escuela.backend.billing.repository.PaymentReceiptRepository;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class FindOwnPaymentReceiptsService implements FindOwnPaymentReceiptsUseCase {

    private final PaymentReceiptRepository paymentReceiptRepository;

    public FindOwnPaymentReceiptsService(PaymentReceiptRepository paymentReceiptRepository) {
        this.paymentReceiptRepository = paymentReceiptRepository;
    }

    @Override
    public List<PaymentReceipt> findOwn(Long studentId) {
        return paymentReceiptRepository.findByStudentId(studentId);
    }
}
