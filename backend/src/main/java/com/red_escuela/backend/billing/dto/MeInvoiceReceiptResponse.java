package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.billing.entity.PaymentReceipt;

import java.nio.file.Path;
import java.time.LocalDateTime;

public record MeInvoiceReceiptResponse(Long id, String status, String fileName, LocalDateTime uploadedAt, String rejectionReason) {

    public static MeInvoiceReceiptResponse from(PaymentReceipt receipt) {
        return new MeInvoiceReceiptResponse(
                receipt.getId(), receipt.getStatus().name(), Path.of(receipt.getFilePath()).getFileName().toString(),
                receipt.getUploadedAt(), receipt.getRejectionReason());
    }
}
