package com.red_escuela.backend.billing.usecase.paymentreceipt;

import com.red_escuela.backend.billing.entity.BankTransferReceipt;

import java.util.List;

public interface FindOwnPaymentReceiptsUseCase {
    List<BankTransferReceipt> findOwn(Long studentId);
}
