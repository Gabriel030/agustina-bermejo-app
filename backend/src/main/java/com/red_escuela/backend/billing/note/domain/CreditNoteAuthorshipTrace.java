package com.red_escuela.backend.billing.note.domain;

import java.time.Instant;

public record CreditNoteAuthorshipTrace(String requestedByName, String authorizedByName, Instant authorizedAt) {

    public static CreditNoteAuthorshipTrace unknown() {
        return new CreditNoteAuthorshipTrace(null, null, null);
    }
}
