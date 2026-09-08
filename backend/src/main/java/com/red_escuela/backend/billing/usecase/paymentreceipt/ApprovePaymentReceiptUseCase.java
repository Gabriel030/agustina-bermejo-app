package com.red_escuela.backend.billing.usecase.paymentreceipt;

import com.red_escuela.backend.billing.entity.PaymentReceipt;
import com.red_escuela.backend.identity.entity.Person;

public interface ApprovePaymentReceiptUseCase {
    PaymentReceipt approve(Long id, Person decidedBy);
}
