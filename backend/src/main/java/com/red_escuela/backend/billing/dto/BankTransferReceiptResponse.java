package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.billing.entity.BankTransferReceipt;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.identity.entity.Person;

import java.time.LocalDateTime;

public record BankTransferReceiptResponse(Long id, Long invoiceId, String filePath, String status, String rejectionReason,
                                     LocalDateTime uploadedAt, String studentName, String studentDni,
                                     String invoicePeriod, String invoiceFeeType, String productName) {

    public static BankTransferReceiptResponse from(BankTransferReceipt receipt, String productName) {
        Invoice invoice = receipt.getInvoice();
        Person payer = receipt.payer();
        return new BankTransferReceiptResponse(
                receipt.getId(), invoice.getId(), receipt.getFilePath(),
                receipt.getStatus().name(), receipt.getRejectionReason(), receipt.getUploadedAt(),
                payer == null ? null : payer.getName() + " " + payer.getLastname(),
                payer == null ? null : payer.getDni(),
                invoice.getPrimaryLine().getPeriod().toString(),
                invoice.getPrimaryLine().getFeeType().name(),
                productName);
    }
}
