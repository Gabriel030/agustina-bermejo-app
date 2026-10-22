package com.red_escuela.backend.billing.usecase.paymentreceipt;

import com.red_escuela.backend.billing.entity.BankTransferReceipt;
import com.red_escuela.backend.billing.operation.PaymentVerification;
import com.red_escuela.backend.billing.operation.BankTransferReceiptReviewPort;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ApprovePaymentReceiptService implements ApprovePaymentReceiptUseCase {
    private final BankTransferReceiptReviewPort reviewer;

    public ApprovePaymentReceiptService(BankTransferReceiptReviewPort reviewer) {
        this.reviewer = reviewer;
    }

    @Override
    public BankTransferReceipt approve(Long id, Person decidedBy) {
        return reviewer.approveReceipt(id, decidedBy, PaymentVerification.none());
    }

    @Override
    public BankTransferReceipt approve(Long id, Person decidedBy, PaymentVerification verification) {
        return reviewer.approveReceipt(id, decidedBy, verification);
    }
}
