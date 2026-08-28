package com.red_escuela.backend.dto;

import com.red_escuela.backend.entity.PaymentReceipt;

import java.time.LocalDateTime;

public record PaymentReceiptResponse(
        Long id, Long invoiceId, String filePath, LocalDateTime uploadedAt,
        String status, String rejectionReason, Long reviewedById, LocalDateTime reviewedAt) {

    public static PaymentReceiptResponse from(PaymentReceipt receipt) {
        return new PaymentReceiptResponse(
                receipt.getId(), receipt.getInvoice().getId(), receipt.getFilePath(), receipt.getUploadedAt(),
                receipt.getStatus().name(), receipt.getRejectionReason(),
                receipt.getReviewedBy() == null ? null : receipt.getReviewedBy().getId(),
                receipt.getReviewedAt());
    }
}
