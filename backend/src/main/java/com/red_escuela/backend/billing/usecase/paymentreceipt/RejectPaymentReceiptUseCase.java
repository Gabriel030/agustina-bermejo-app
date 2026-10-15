package com.red_escuela.backend.billing.usecase.paymentreceipt;

import com.red_escuela.backend.billing.entity.BankTransferReceipt;
import com.red_escuela.backend.identity.entity.Person;

public interface RejectPaymentReceiptUseCase {
    BankTransferReceipt reject(Long id, String motivo, Person decidedBy);
}
