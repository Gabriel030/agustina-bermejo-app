package com.red_escuela.backend.billing.usecase.paymentreceipt;

import com.red_escuela.backend.billing.entity.PaymentReceipt;

import java.util.List;

public interface ListPendingPaymentReceiptsUseCase {
    List<PaymentReceipt> list();
}
