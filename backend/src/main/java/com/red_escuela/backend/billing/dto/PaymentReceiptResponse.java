package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.PaymentReceipt;
import com.red_escuela.backend.identity.entity.Person;

import java.time.LocalDateTime;

public record PaymentReceiptResponse(Long id, Long invoiceId, String filePath, String status, String rejectionReason,
                                     LocalDateTime uploadedAt, String studentName, String studentDni,
                                     String invoicePeriod, String invoiceFeeType, String productName) {

    public static PaymentReceiptResponse from(PaymentReceipt receipt) {
        Invoice invoice = receipt.getInvoice();
        Person payer = receipt.payer();
        return new PaymentReceiptResponse(
                receipt.getId(), invoice.getId(), receipt.getFilePath(),
                receipt.getStatus().name(), receipt.getRejectionReason(), receipt.getUploadedAt(),
                payer == null ? null : payer.getName() + " " + payer.getLastname(),
                payer == null ? null : payer.getDni(),
                invoice.getPeriod() != null ? invoice.getPeriod().toString() : null,
                invoice.getFeeType().name(),
                productNameOf(invoice));
    }

    private static String productNameOf(Invoice invoice) {
        if (invoice.getCourseEnrollment() != null) {
            return invoice.getCourseEnrollment().getCourse().getName();
        }
        if (invoice.getProgramEnrollment() != null) {
            return invoice.getProgramEnrollment().getProgram().getName();
        }
        return null;
    }
}
