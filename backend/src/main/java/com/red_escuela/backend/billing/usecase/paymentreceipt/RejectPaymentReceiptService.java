package com.red_escuela.backend.billing.usecase.paymentreceipt;

import com.red_escuela.backend.billing.entity.BankTransferReceipt;
import com.red_escuela.backend.billing.operation.BankTransferReceiptReviewPort;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class RejectPaymentReceiptService implements RejectPaymentReceiptUseCase {
    private final BankTransferReceiptReviewPort reviewer;

    public RejectPaymentReceiptService(BankTransferReceiptReviewPort reviewer) {
        this.reviewer = reviewer;
    }

    @Override
    public BankTransferReceipt reject(Long id, String motivo, Person decidedBy) {
        return reviewer.rejectReceipt(id, motivo, decidedBy);
    }
}
