package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.PaymentReceipt;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;

import java.time.LocalDateTime;

public record PaymentReceiptResponse(Long id, Long invoiceId, String filePath, String status, String rejectionReason,
                                     LocalDateTime uploadedAt, String studentName, String studentDni,
                                     String invoicePeriod, String invoiceFeeType) {

    public static PaymentReceiptResponse from(PaymentReceipt receipt) {
        Invoice invoice = receipt.getInvoice();
        String studentName = null;
        String studentDni = null;
        CourseEnrollment ce = invoice.getCourseEnrollment();
        ProgramEnrollment pe = invoice.getProgramEnrollment();
        if (ce != null) {
            studentName = ce.getStudent().getPerson().getName() + " " + ce.getStudent().getPerson().getLastname();
            studentDni = ce.getStudent().getPerson().getDni();
        } else if (pe != null) {
            studentName = pe.getStudent().getPerson().getName() + " " + pe.getStudent().getPerson().getLastname();
            studentDni = pe.getStudent().getPerson().getDni();
        }
        return new PaymentReceiptResponse(
                receipt.getId(), invoice.getId(), receipt.getFilePath(),
                receipt.getStatus().name(), receipt.getRejectionReason(), receipt.getUploadedAt(),
                studentName, studentDni,
                invoice.getPeriod() != null ? invoice.getPeriod().toString() : null,
                invoice.getFeeType().name());
    }
}
