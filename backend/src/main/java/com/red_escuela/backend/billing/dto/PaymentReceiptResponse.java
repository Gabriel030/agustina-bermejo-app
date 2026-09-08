package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.billing.entity.PaymentReceipt;

import java.time.LocalDateTime;

public record PaymentReceiptResponse(Long id, Long invoiceId, String filePath, String status, LocalDateTime uploadedAt) {

    public static PaymentReceiptResponse from(PaymentReceipt receipt) {
        return new PaymentReceiptResponse(
                receipt.getId(), receipt.getInvoice().getId(), receipt.getFilePath(),
                receipt.getStatus().name(), receipt.getUploadedAt());
    }
}
