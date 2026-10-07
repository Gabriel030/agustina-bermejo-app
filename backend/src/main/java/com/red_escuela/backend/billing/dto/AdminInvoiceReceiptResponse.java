package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.billing.entity.BankTransferReceipt;
import com.red_escuela.backend.identity.entity.Person;

import java.nio.file.Path;
import java.time.LocalDateTime;

public record AdminInvoiceReceiptResponse(
        Long id, String status, String fileName, LocalDateTime uploadedAt, String uploadedByName,
        String reviewedByName, LocalDateTime reviewedAt, String rejectionReason) {

    public static AdminInvoiceReceiptResponse from(BankTransferReceipt receipt) {
        return new AdminInvoiceReceiptResponse(
                receipt.getId(), receipt.getStatus().name(), Path.of(receipt.getFilePath()).getFileName().toString(),
                receipt.getUploadedAt(), nameOf(receipt.getUploadedBy()), nameOf(receipt.getReviewedBy()),
                receipt.getReviewedAt(), receipt.getRejectionReason());
    }

    private static String nameOf(Person person) {
        return person == null ? null : person.getName() + " " + person.getLastname();
    }
}
