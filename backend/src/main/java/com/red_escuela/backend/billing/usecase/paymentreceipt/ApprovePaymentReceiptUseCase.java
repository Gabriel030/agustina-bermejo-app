package com.red_escuela.backend.billing.usecase.paymentreceipt;

import com.red_escuela.backend.billing.entity.BankTransferReceipt;
import com.red_escuela.backend.billing.operation.PaymentVerification;
import com.red_escuela.backend.identity.entity.Person;

public interface ApprovePaymentReceiptUseCase {

    BankTransferReceipt approve(Long id, Person decidedBy, PaymentVerification verification);
}
