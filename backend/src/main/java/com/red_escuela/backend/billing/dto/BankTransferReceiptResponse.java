package com.red_escuela.backend.billing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.red_escuela.backend.billing.entity.BankTransferReceipt;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.dto.ReceiptInvoiceSummary;
import com.red_escuela.backend.billing.invoice.dto.ReceiptUploader;
import com.red_escuela.backend.billing.invoice.dto.ReceiptReviewDetail;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record BankTransferReceiptResponse(Long id, Long invoiceId, String filePath, String status, @Schema(nullable = true) String rejectionReason, @Schema(nullable = true) String rejectionCode,
                                     LocalDateTime uploadedAt, String studentName, String studentDni,
                                     String invoicePeriod, String invoiceFeeType, String productName, List<Long> invoiceIds,
                                     Money declaredAmount, LocalDate transferDate, String operationNumber, String originAccount, String holderName,
                                     List<ReceiptInvoiceSummary> invoices, Integer pendingBusinessDays, Boolean reviewAlertSent,
                                     LocalDateTime reviewAlertSentAt, ReceiptUploader uploadedBy) {

    public static BankTransferReceiptResponse from(BankTransferReceipt receipt, Person payer, String productName, List<Long> invoiceIds) {
        return from(receipt, payer, productName, invoiceIds, null, null);
    }

    public static BankTransferReceiptResponse from(BankTransferReceipt receipt, Person payer, String productName, List<Long> invoiceIds,
                                                   ReceiptReviewDetail detail, ReceiptUploader uploadedBy) {
        Invoice invoice = receipt.getInvoice();
        return new BankTransferReceiptResponse(
                receipt.getId(), invoice.getId(), receipt.getFilePath(),
                receipt.getStatus().name(), receipt.getRejectionReason(), codeOf(receipt), receipt.getUploadedAt(),
                payer == null ? null : payer.getName() + " " + payer.getLastname(),
                payer == null ? null : payer.getDni(),
                invoice.getPrimaryLine().getPeriod().toString(),
                invoice.getPrimaryLine().getFeeType().name(),
                productName,
                invoiceIds,
                detail == null ? null : detail.declaredAmount(),
                detail == null ? null : detail.transferDate(),
                detail == null ? null : detail.operationNumber(),
                detail == null ? null : detail.originAccount(),
                detail == null ? null : detail.holderName(),
                detail == null ? null : detail.invoices(),
                detail == null ? null : detail.pendingBusinessDays(),
                detail == null ? null : detail.reviewAlertSentAt() != null,
                detail == null ? null : detail.reviewAlertSentAt(),
                uploadedBy);
    }

    public BankTransferReceiptResponse withoutReviewAlertState() {
        return new BankTransferReceiptResponse(id, invoiceId, filePath, status, rejectionReason, rejectionCode, uploadedAt, studentName, studentDni, invoicePeriod,
                invoiceFeeType, productName, invoiceIds, declaredAmount, transferDate, operationNumber, originAccount, holderName, invoices, pendingBusinessDays,
                null, reviewAlertSentAt, null);
    }

    private static String codeOf(BankTransferReceipt receipt) {
        return receipt.getRejectionCode() == null ? null : receipt.getRejectionCode().name();
    }
}
