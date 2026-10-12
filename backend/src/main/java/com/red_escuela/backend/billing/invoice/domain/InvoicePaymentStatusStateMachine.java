package com.red_escuela.backend.billing.invoice.domain;

import com.red_escuela.backend.billing.invoice.exception.IllegalInvoicePaymentStatusTransitionException;
import com.red_escuela.backend.shared.domain.statemachine.StateTransitionTable;

import static com.red_escuela.backend.billing.invoice.domain.InvoicePaymentEvent.*;
import static com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus.CANCELLED;
import static com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus.OVERDUE;
import static com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus.PAID;
import static com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus.PAYMENT_UNDER_REVIEW;
import static com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus.PENDING;

public final class InvoicePaymentStatusStateMachine {

    public static final StateTransitionTable<InvoicePaymentStatus, InvoicePaymentEvent> TRANSITIONS =
            StateTransitionTable.forStatesAndEvents(InvoicePaymentStatus.class, InvoicePaymentEvent.class)
                    .allowInitialTransition(ISSUED_WITH_AMOUNT_DUE, PENDING)
                    .allowInitialTransition(ISSUED_WITH_ZERO_TOTAL, PAID)
                    .allowTransition(PENDING, BANK_TRANSFER_RECEIPT_SUBMITTED_ON_TIME, PAYMENT_UNDER_REVIEW)
                    .allowTransition(PENDING, FULL_PAYMENT_ALLOCATED, PAID)
                    .allowTransition(PENDING, DUE_DATE_PASSED, OVERDUE)
                    .allowTransition(PENDING, CANCELLATION_CREDIT_NOTE_APPLIED, CANCELLED)
                    .allowTransition(PENDING, DEBT_REDUCTION_CREDIT_NOTE_SETTLED_INVOICE, PAID)
                    .allowTransition(PENDING, PARTIAL_DEBT_REDUCTION_CREDIT_NOTE_APPLIED, PENDING)
                    .allowTransition(PAYMENT_UNDER_REVIEW, BANK_TRANSFER_RECEIPT_REJECTED_BEFORE_DUE_DATE_PASSED, PENDING)
                    .allowTransition(PAYMENT_UNDER_REVIEW, BANK_TRANSFER_RECEIPT_REJECTED_AFTER_DUE_DATE_PASSED, OVERDUE)
                    .allowTransition(PAYMENT_UNDER_REVIEW, FULL_PAYMENT_ALLOCATED, PAID)
                    .allowTransition(PAID, PAYMENT_ALLOCATION_REVERSED_BEFORE_DUE_DATE_PASSED, PENDING)
                    .allowTransition(PAID, PAYMENT_ALLOCATION_REVERSED_AFTER_DUE_DATE_PASSED, OVERDUE)
                    .allowTransition(PAID, CREDIT_NOTE_APPLIED_TO_PAID_INVOICE, PAID)
                    .allowTransition(OVERDUE, FULL_PAYMENT_ALLOCATED, PAID)
                    .allowTransition(OVERDUE, CANCELLATION_CREDIT_NOTE_APPLIED, CANCELLED)
                    .allowTransition(OVERDUE, DEBT_REDUCTION_CREDIT_NOTE_SETTLED_INVOICE, PAID)
                    .allowTransition(OVERDUE, PARTIAL_DEBT_REDUCTION_CREDIT_NOTE_APPLIED, OVERDUE)
                    .buildTransitionTable();

    private InvoicePaymentStatusStateMachine() {
    }

    public static InvoicePaymentStatus resolveNextStatus(InvoicePaymentStatus fromStatus, InvoicePaymentEvent event) {
        return TRANSITIONS.findTargetStateFor(fromStatus, event)
                .orElseThrow(() -> new IllegalInvoicePaymentStatusTransitionException(fromStatus, event));
    }
}
