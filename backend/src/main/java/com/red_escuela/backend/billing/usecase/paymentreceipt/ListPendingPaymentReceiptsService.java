package com.red_escuela.backend.billing.usecase.paymentreceipt;

import com.red_escuela.backend.billing.entity.PaymentReceipt;
import com.red_escuela.backend.billing.entity.PaymentReceiptStatus;
import com.red_escuela.backend.billing.repository.PaymentReceiptRepository;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListPendingPaymentReceiptsService implements ListPendingPaymentReceiptsUseCase {

    private final PaymentReceiptRepository paymentReceiptRepository;

    public ListPendingPaymentReceiptsService(PaymentReceiptRepository paymentReceiptRepository) {
        this.paymentReceiptRepository = paymentReceiptRepository;
    }

    @Override
    public List<PaymentReceipt> list() {
        return paymentReceiptRepository.findByStatusOrderByUploadedAtAsc(PaymentReceiptStatus.PENDING_REVIEW);
    }
}
