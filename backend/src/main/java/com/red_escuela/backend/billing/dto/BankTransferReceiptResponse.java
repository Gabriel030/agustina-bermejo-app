package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.billing.entity.BankTransferReceipt;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.identity.entity.Person;

import java.time.LocalDateTime;
import java.util.List;

public record BankTransferReceiptResponse(Long id, Long invoiceId, String filePath, String status, String rejectionReason,
                                     LocalDateTime uploadedAt, String studentName, String studentDni,
                                     String invoicePeriod, String invoiceFeeType, String productName, List<Long> invoiceIds) {

    public static BankTransferReceiptResponse from(BankTransferReceipt receipt, Person payer, String productName, List<Long> invoiceIds) {
        Invoice invoice = receipt.getInvoice();
        return new BankTransferReceiptResponse(
                receipt.getId(), invoice.getId(), receipt.getFilePath(),
                receipt.getStatus().name(), receipt.getRejectionReason(), receipt.getUploadedAt(),
                payer == null ? null : payer.getName() + " " + payer.getLastname(),
                payer == null ? null : payer.getDni(),
                invoice.getPrimaryLine().getPeriod().toString(),
                invoice.getPrimaryLine().getFeeType().name(),
                productName,
                invoiceIds);
    }
}
