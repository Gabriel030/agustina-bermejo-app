package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.billing.entity.BankTransferReceipt;

import java.nio.file.Path;
import java.time.LocalDateTime;

public record MeInvoiceReceiptResponse(Long id, String status, String fileName, LocalDateTime uploadedAt, String rejectionReason, String rejectionCode) {

    public static MeInvoiceReceiptResponse from(BankTransferReceipt receipt) {
        return new MeInvoiceReceiptResponse(
                receipt.getId(), receipt.getStatus().name(), Path.of(receipt.getFilePath()).getFileName().toString(),
                receipt.getUploadedAt(), receipt.getRejectionReason(),
                receipt.getRejectionCode() == null ? null : receipt.getRejectionCode().name());
    }
}
