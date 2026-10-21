package com.red_escuela.backend.billing.payment.dto;

import com.red_escuela.backend.billing.payment.domain.BankTransferReceiptSubmissionResult;
import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDate;
import java.util.List;

public record BankTransferReceiptSubmissionResponse(Long receiptId, String receiptStatus, Long paymentId, String paymentStatus, Money declaredAmount,
                                                    LocalDate uploadDate, List<ItemResponse> items) {
    private static final String RECEIPT_PENDING_REVIEW = "PENDING_REVIEW";
    private static final String PAYMENT_PENDING = "PENDING";

    public static BankTransferReceiptSubmissionResponse from(BankTransferReceiptSubmissionResult result) {
        return new BankTransferReceiptSubmissionResponse(result.receiptId(), RECEIPT_PENDING_REVIEW, result.paymentId(), PAYMENT_PENDING, result.declaredAmount().value(),
                result.uploadDate(), result.items().stream()
                .map(item -> new ItemResponse(item.invoiceId(), item.position(), item.outstandingAtSelection().value(), item.markedUnderReview())).toList());
    }

    public record ItemResponse(Long invoiceId, int position, Money outstandingAtSelection, boolean protectedFromOverdue) {
    }
}
