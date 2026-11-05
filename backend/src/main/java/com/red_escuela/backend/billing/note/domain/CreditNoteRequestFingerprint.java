package com.red_escuela.backend.billing.note.domain;

import java.util.List;

record CreditNoteRequestFingerprint(Long invoiceId, CreditNoteReason reason, String justification, List<CreditNoteLineAmount> lines, Long requestedByPersonId) {
}
