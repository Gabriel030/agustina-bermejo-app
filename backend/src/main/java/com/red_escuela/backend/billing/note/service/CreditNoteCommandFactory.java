package com.red_escuela.backend.billing.note.service;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.service.InvoiceCreditAccessService;
import com.red_escuela.backend.billing.note.domain.CancelInvoiceCommand;
import com.red_escuela.backend.billing.note.domain.CreditNoteDestination;
import com.red_escuela.backend.billing.note.domain.CreditNoteLineAmount;
import com.red_escuela.backend.billing.note.domain.CreditNoteReason;
import com.red_escuela.backend.billing.note.domain.IssueCreditNoteCommand;
import com.red_escuela.backend.billing.note.exception.CreditNoteInvoiceNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class CreditNoteCommandFactory {
    private final InvoiceCreditAccessService invoices;
    private final CreditNoteActorResolver actorResolver;

    public CreditNoteCommandFactory(InvoiceCreditAccessService invoices, CreditNoteActorResolver actorResolver) {
        this.invoices = invoices;
        this.actorResolver = actorResolver;
    }

    public CancelInvoiceCommand buildCancellationCommandForCurrentUser(Long invoiceId, CreditNoteReason reason, String justification) {
        return CancelInvoiceCommand.byPerson(invoiceId, reason, justification, actorResolver.currentPersonId(), actorResolver.currentActorIsDirector());
    }

    @Transactional(readOnly = true)
    public IssueCreditNoteCommand buildCommandForCurrentUser(Long invoiceId, CreditNoteReason reason, String justification, List<CreditNoteLineAmount> lines) {
        Invoice invoice = invoices.findInvoice(invoiceId).orElseThrow(() -> new CreditNoteInvoiceNotFoundException(invoiceId));
        CreditNoteDestination destination = CreditNoteDestination.ofInvoiceStatus(invoice.getPaymentStatus());
        return new IssueCreditNoteCommand(invoiceId, reason, justification, lines, destination, actorResolver.currentPersonId(),
                actorResolver.currentActorIsDirector(), null);
    }
}
