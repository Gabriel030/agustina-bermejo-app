package com.red_escuela.backend.billing.note.service;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.service.InvoiceFollowUpQueries;
import com.red_escuela.backend.shared.domain.Money;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
public class InvoiceBriefReader {
    private final InvoiceFollowUpQueries invoices;

    public InvoiceBriefReader(InvoiceFollowUpQueries invoices) {
        this.invoices = invoices;
    }

    @Transactional(readOnly = true)
    public Optional<InvoiceBrief> findBriefOf(Long invoiceId) {
        return invoices.findInvoice(invoiceId).map(InvoiceBriefReader::briefOf);
    }

    private static InvoiceBrief briefOf(Invoice invoice) {
        String number = invoice.getInvoiceNumber() == null ? "sin número" : invoice.getInvoiceNumber().value();
        String concept = invoice.getLines().isEmpty() ? null : invoice.getPrimaryLine().getConcept();
        return new InvoiceBrief(invoice.getId(), number, concept, invoice.getStudentSnapshot().fullName(), invoice.getAmounts().total().value());
    }

    public record InvoiceBrief(Long invoiceId, String number, String concept, String studentName, Money total) {
        public String label() {
            return concept == null || concept.isBlank() ? number : number + " · " + concept.strip();
        }
    }
}
