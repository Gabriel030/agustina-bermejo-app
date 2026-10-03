package com.red_escuela.backend.billing.usecase.paymentreceipt;

public interface GetPaymentReceiptFileUseCase {
    PaymentReceiptFile get(Long receiptId);
}
