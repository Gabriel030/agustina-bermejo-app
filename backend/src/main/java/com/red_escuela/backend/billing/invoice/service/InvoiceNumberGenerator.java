package com.red_escuela.backend.billing.invoice.service;

import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceNumber;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;

@Service
public class InvoiceNumberGenerator {

    @PersistenceContext
    private EntityManager entityManager;

    public InvoiceNumber nextInvoiceNumber(InvoiceDocumentType documentType) {
        String sequence = documentType == InvoiceDocumentType.INVOICE ? "invoice_number_seq" : "debit_note_number_seq";
        Number next = (Number) entityManager.createNativeQuery("select nextval('" + sequence + "')").getSingleResult();
        return InvoiceNumber.formatInvoiceNumber(documentType, next.longValue());
    }
}
