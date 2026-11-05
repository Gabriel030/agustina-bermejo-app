package com.red_escuela.backend.architecture.fixture.billing.collections;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.note.domain.CreditNote;
import com.red_escuela.backend.billing.note.domain.LateFeeCorrectionCreditNoteKind;
import com.red_escuela.backend.billing.note.service.LateFeeCorrectionCreditNoteIssuer;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.time.LocalDate;

public class LateFeeCorrectionShortcutFixture {

    public CreditNote issueWithoutTheOperation(LateFeeCorrectionCreditNoteIssuer issuer, Invoice debitNote, NonNegativeMoney credit) {
        return issuer.issueCorrectionCreditNote(debitNote, credit, "x", LocalDate.of(2027, 5, 3), LateFeeCorrectionCreditNoteKind.AFTER_WITHDRAWAL);
    }
}
