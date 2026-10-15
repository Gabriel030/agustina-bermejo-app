package com.red_escuela.backend.billing.note.service;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceCreditNoteApplication;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.invoice.domain.event.InvoiceCancelledEvent;
import com.red_escuela.backend.billing.note.domain.CreditNote;
import com.red_escuela.backend.billing.note.domain.CreditNoteLineRequest;
import com.red_escuela.backend.billing.note.domain.CreditNoteReason;
import com.red_escuela.backend.billing.note.repository.CreditNoteRepository;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Component
public class LateFeeCorrectionCreditNoteIssuer {
    public static final String PROCESS_NAME = "LateFeeWithdrawalCorrection";
    private static final StatusChangeActor ACTOR = StatusChangeActor.ofProcess(PROCESS_NAME);

    private final CreditNoteRepository creditNoteRepository;
    private final CreditNoteNumberGenerator numberGenerator;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public LateFeeCorrectionCreditNoteIssuer(CreditNoteRepository creditNoteRepository, CreditNoteNumberGenerator numberGenerator,
                                             ApplicationEventPublisher eventPublisher, Clock clock) {
        this.creditNoteRepository = creditNoteRepository;
        this.numberGenerator = numberGenerator;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    public CreditNote issueCorrectionCreditNote(Invoice lockedDebitNote, NonNegativeMoney credit, String justification,
                                                LocalDate factDate) {
        LocalDate businessDate = LocalDate.now(clock);
        boolean coversAllTheUnpaidDebt = lockedDebitNote.getPaymentStatus() != InvoicePaymentStatus.PAID
                && credit.equals(lockedDebitNote.getAmounts().outstandingAmount());
        CreditNote note;
        InvoiceCreditNoteApplication application;
        if (coversAllTheUnpaidDebt) {
            note = CreditNote.issueCancellationCreditNoteForInvoice(lockedDebitNote, CreditNoteReason.LATE_FEE_CORRECTION, justification,
                    numberGenerator.nextCreditNoteNumber(), businessDate, factDate, null, ACTOR);
            application = note.toInvoiceCancellationApplication(false);
        } else {
            note = CreditNote.issueCreditNoteForInvoice(lockedDebitNote, CreditNoteReason.LATE_FEE_CORRECTION, justification,
                    List.of(new CreditNoteLineRequest(lockedDebitNote.getPrimaryLine().getLineNumber(), credit)), numberGenerator.nextCreditNoteNumber(),
                    businessDate, factDate, null, ACTOR);
            application = note.toInvoiceCreditNoteApplication(lockedDebitNote.getAmounts().outstandingAmount());
        }
        lockedDebitNote.applyCreditNote(application, businessDate, ACTOR);
        creditNoteRepository.save(note);
        lockedDebitNote.pullRecordedDomainEvents().stream().filter(InvoiceCancelledEvent.class::isInstance).forEach(eventPublisher::publishEvent);
        return note;
    }
}
