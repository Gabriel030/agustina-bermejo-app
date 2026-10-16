package com.red_escuela.backend.billing.invoice.service;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;

import java.util.List;

public record InvoiceView(Invoice invoice, ProgramEnrollment programEnrollment, CourseEnrollment courseEnrollment, List<InvoicePaymentShare> paymentShares,
                          List<InvoiceCreditNoteSourceShare> creditNoteShares) {

    public InvoiceView(Invoice invoice, ProgramEnrollment programEnrollment, CourseEnrollment courseEnrollment, List<InvoicePaymentShare> paymentShares) {
        this(invoice, programEnrollment, courseEnrollment, paymentShares, List.of());
    }
}
