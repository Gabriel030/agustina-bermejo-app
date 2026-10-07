package com.red_escuela.backend.billing.invoice.domain;

import com.red_escuela.backend.billing.invoice.exception.IllegalInvoiceDocumentStatusTransitionException;
import com.red_escuela.backend.shared.domain.statemachine.StateTransitionTable;

import static com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentEvent.DRAFT_CONTENT_CHANGED;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentEvent.DRAFT_CREATED;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentEvent.DRAFT_DISCARDED;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentEvent.DRAFT_SUBMITTED_FOR_PAYMENT;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentEvent.INVOICE_ISSUED;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentStatus.AWAITING_PAYMENT;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentStatus.DISCARDED;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentStatus.DRAFT;
import static com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentStatus.ISSUED;

public final class InvoiceDocumentStatusStateMachine {

    public static final StateTransitionTable<InvoiceDocumentStatus, InvoiceDocumentEvent> TRANSITIONS =
            StateTransitionTable.forStatesAndEvents(InvoiceDocumentStatus.class, InvoiceDocumentEvent.class)
                    .allowInitialTransition(DRAFT_CREATED, DRAFT)
                    .allowTransition(DRAFT, DRAFT_CONTENT_CHANGED, DRAFT)
                    .allowTransition(DRAFT, DRAFT_SUBMITTED_FOR_PAYMENT, AWAITING_PAYMENT)
                    .allowTransition(DRAFT, INVOICE_ISSUED, ISSUED)
                    .allowTransition(DRAFT, DRAFT_DISCARDED, DISCARDED)
                    .allowTransition(AWAITING_PAYMENT, INVOICE_ISSUED, ISSUED)
                    .allowTransition(AWAITING_PAYMENT, DRAFT_DISCARDED, DISCARDED)
                    .buildTransitionTable();

    private InvoiceDocumentStatusStateMachine() {
    }

    public static InvoiceDocumentStatus resolveNextStatus(InvoiceDocumentStatus fromStatus, InvoiceDocumentEvent event) {
        return TRANSITIONS.findTargetStateFor(fromStatus, event)
                .orElseThrow(() -> new IllegalInvoiceDocumentStatusTransitionException(fromStatus, event));
    }
}
