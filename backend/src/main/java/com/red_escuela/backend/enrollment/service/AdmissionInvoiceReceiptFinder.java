package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.billing.api.AdmissionReceiptLookup;
import com.red_escuela.backend.billing.api.AdmissionReceiptSummary;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdmissionInvoiceReceiptFinder {

    private final AdmissionReceiptLookup receiptLookup;

    public AdmissionInvoiceReceiptFinder(AdmissionReceiptLookup receiptLookup) {
        this.receiptLookup = receiptLookup;
    }

    public AdmissionReceiptSummary findLatestReceiptByAdmissionInvoiceId(Long admissionInvoiceId) {
        return receiptLookup.findLatestReceiptByInvoiceIds(List.of(admissionInvoiceId)).get(admissionInvoiceId);
    }
}
