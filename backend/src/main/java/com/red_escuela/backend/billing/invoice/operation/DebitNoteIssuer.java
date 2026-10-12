package com.red_escuela.backend.billing.invoice.operation;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDraftHeader;
import com.red_escuela.backend.billing.invoice.domain.InvoiceFactory;
import com.red_escuela.backend.billing.invoice.domain.InvoiceLineDraft;
import com.red_escuela.backend.billing.invoice.domain.StudentSnapshot;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.invoice.service.InvoiceNumberGenerator;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class DebitNoteIssuer {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceNumberGenerator numberGenerator;

    public DebitNoteIssuer(InvoiceRepository invoiceRepository, InvoiceNumberGenerator numberGenerator) {
        this.invoiceRepository = invoiceRepository;
        this.numberGenerator = numberGenerator;
    }

    public Invoice issueLateFeeDebitNote(InvoiceDraftHeader header, InvoiceLineDraft line, LocalDate issueDate, LocalDate dueDate, StudentSnapshot recipient,
                                         StatusChangeActor actor) {
        Invoice debitNote = InvoiceFactory.createLateFeeDebitNoteDraft(header, line, actor);
        debitNote.issueInvoice(numberGenerator.nextInvoiceNumber(InvoiceDocumentType.DEBIT_NOTE), issueDate, dueDate, recipient, actor);
        Invoice issued = invoiceRepository.save(debitNote);
        invoiceRepository.flush();
        return issued;
    }
}
