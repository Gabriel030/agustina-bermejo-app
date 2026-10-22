package com.red_escuela.backend.billing.operation;

import com.red_escuela.backend.billing.entity.BankTransferReceipt;
import com.red_escuela.backend.identity.entity.Person;

public interface BankTransferReceiptReviewPort {

    BankTransferReceipt approveReceipt(Long id, Person decidedBy, PaymentVerification verification);

    BankTransferReceipt rejectReceipt(Long id, String reason, Person decidedBy);
}
